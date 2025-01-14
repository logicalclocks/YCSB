/*
 * Copyright (c) 2023, Hopsworks AB. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

/**
 * RonDB client binding for YCSB.
 */

package site.ycsb.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.ycsb.*;
import site.ycsb.db.clusterj.ClusterJClient;
import site.ycsb.db.grpc.GrpcClient;
import site.ycsb.db.http.RestApiClient;
import site.ycsb.workloads.CoreWorkload;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * This is the REST API client for RonDB.
 */
public class RonDBClient extends DB {
  protected static Logger logger = LoggerFactory.getLogger(RonDBClient.class);
  private DB dbReadClient;
  private DB clusterJClient;
  private static Object lock = new Object();
  private long fieldCount;
  private Set<String> fieldNames;
  private static int maxThreadID = 0;
  private int threadID = 0;
  private static CountDownLatch threadCompletionCount;

  public RonDBClient() {
  }

  /**
   * Initialize any state for this DB.
   * Called once per DB instance; there is one DB instance per client thread.
   */
  public void init() throws DBException {
    Properties properties = getProperties();
    synchronized (lock) {
      if (threadCompletionCount == null) {
        threadCompletionCount = new CountDownLatch(
            Integer.parseInt(properties.getProperty(Client.THREAD_COUNT_PROPERTY, "1")));
      }

      threadID = maxThreadID++;

      String readApiPropStr = properties.getProperty(ConfigKeys.RONDB_READ_API_TYPE_KEY,
          ConfigKeys.RONDB_READ_API_TYPE_DEFAULT);

      // writer
      clusterJClient = initClusterJClient(properties);

      // reader
      if (readApiPropStr.compareToIgnoreCase(RonDBAPIType.CLUSTERJ.toString()) == 0) {
        dbReadClient = clusterJClient; // same client
      } else {
        dbReadClient = initClient(readApiPropStr, properties);
      }

      fieldCount = Long.parseLong(properties.getProperty(CoreWorkload.FIELD_COUNT_PROPERTY,
          CoreWorkload.FIELD_COUNT_PROPERTY_DEFAULT));
      final String fieldNamePrefix = properties.getProperty(CoreWorkload.FIELD_NAME_PREFIX,
          CoreWorkload.FIELD_NAME_PREFIX_DEFAULT);
      fieldNames = new HashSet<>();
      for (int i = 0; i < fieldCount; i++) {
        fieldNames.add(fieldNamePrefix + i);
      }
    }
  }

  private DB initClusterJClient(Properties properties) throws DBException {
    DB dbClient;
    try {
      dbClient = new ClusterJClient(properties);
      dbClient.init();
    } catch (Exception e) {
      logger.error("Initialization failed ", e);
      e.printStackTrace();
      if (e instanceof DBException) {
        throw (DBException) e;
      } else {
        throw new DBException(e);
      }
    }
    return dbClient;

  }

  private DB initClient(String clientType, Properties properties) throws DBException {
    DB dbClient = null;
    try {
      if (clientType.compareToIgnoreCase(RonDBAPIType.CLUSTERJ.toString()) == 0) {
        dbClient = new ClusterJClient(properties);
      } else if (clientType.compareToIgnoreCase(RonDBAPIType.REST.toString()) == 0) {
        dbClient = new RestApiClient(threadID, properties);
      } else if (clientType.compareToIgnoreCase(RonDBAPIType.GRPC.toString()) == 0) {
        dbClient = new GrpcClient(threadID, properties);
      } else {
        throw new IllegalArgumentException("Wrong argument " + ConfigKeys.RONDB_READ_API_TYPE_KEY);
      }
      dbClient.init();
    } catch (Exception e) {
      logger.error("Initialization failed ", e);
      e.printStackTrace();
      if (e instanceof DBException) {
        throw (DBException) e;
      } else {
        throw new DBException(e);
      }
    }
    return dbClient;
  }

  /**
   * Cleanup any state for this DB.
   * Called once per DB instance; there is one DB instance per client thread.
   */
  public void cleanup() throws DBException {
    threadCompletionCount.countDown();
    try {
      //stop all threads at the same time
      threadCompletionCount.await();
      dbReadClient.cleanup();
      clusterJClient.cleanup();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Read a record from the database. Each field/value pair from the result will
   * be stored in a HashMap.
   *
   * @param table  The name of the table
   * @param key    The record key of the record to read.
   * @param fields The list of fields to read, or null for all of them
   * @param result A HashMap of field/value pairs for the result
   * @return The result of the operation.
   */
  @Override
  public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    Set<String> fieldsToRead = fields != null ? fields : fieldNames;
    try {
      return dbReadClient.read(table, key, fieldsToRead, result);
    } catch (Exception e) {
      logger.error("Error " + e);
      return Status.ERROR;
    }
  }

  @Override
  public Status batchRead(String table, List<String> keys, List<Set<String>> fields,
                          HashMap<String, HashMap<String, ByteIterator>> results) {
    try {
      return dbReadClient.batchRead(table, keys, fields, results);
    } catch (Exception e) {
      e.printStackTrace();
      logger.error("Error " + e);
      return Status.ERROR;
    }
  }

  @Override
  public Status batchUpdate(String table, List<String> keys,
                            List<Map<String, ByteIterator>>  values) {
    try {
      return clusterJClient.batchUpdate(table, keys, values);
    } catch (Exception e) {
      e.printStackTrace();
      logger.error("Error " + e);
      return Status.ERROR;
    }
  }

  /**
   * Perform a range scan for a set of records in the database.
   * Each field/value pair from the result will be stored in a HashMap.
   *
   * @param table       The name of the table
   * @param startkey    The record key of the first record to read.
   * @param recordcount The number of records to read
   * @param fields      The list of fields to read, or null for all of them
   * @param result      A Vector of HashMaps, where each HashMap is a set
   *                    field/value pairs for one record
   * @return The result of the operation.
   */
  @Override
  public Status scan(String table, String startkey, int recordcount, Set<String> fields,
                     Vector<HashMap<String, ByteIterator>> result) {
    Set<String> fieldsToRead = fields != null ? fields : fieldNames;
    try {
      //scan operation using the writer as Rest API does not yet support scan operations
      return clusterJClient.scan(table, startkey, recordcount, fieldsToRead, result);
    } catch (Exception e) {
      logger.error("Error " + e);
      return Status.ERROR;
    }
  }

  /**
   * Update a record in the database. Any field/value pairs in the specified
   * values
   * HashMap will be written into the record with the specified record key,
   * overwriting any existing values with the same field name.
   *
   * @param table  The name of the table
   * @param key    The record key of the record to write.
   * @param values A HashMap of field/value pairs to update in the record
   * @return The result of the operation.
   */
  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    try {
      return clusterJClient.update(table, key, values);
    } catch (Exception e) {
      logger.error("Error " + e);
      return Status.ERROR;
    }
  }

  /**
   * Insert a record in the database. Any field/value pairs in the specified
   * values
   * HashMap will be written into the record with the specified record key.
   *
   * @param table  The name of the table
   * @param key    The record key of the record to insert.
   * @param values A HashMap of field/value pairs to insert in the record
   * @return The result of the operation.
   */
  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    try {
      return clusterJClient.insert(table, key, values);
    } catch (Exception e) {
      logger.error("Error " + e);
      return Status.ERROR;
    }
  }

  /**
   * Delete a record from the database.
   *
   * @param table The name of the table
   * @param key   The record key of the record to delete.
   * @return The result of the operation.
   */
  @Override
  public Status delete(String table, String key) {
    try {
      return clusterJClient.delete(table, key);
    } catch (Exception e) {
      logger.error("Error " + e);
      return Status.ERROR;
    }
  }

  public static Logger getLogger() {
    return logger;
  }
}
