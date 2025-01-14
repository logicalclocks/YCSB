<!--
Copyright (c) 2011 YCSB++ project, 2014-2023 YCSB contributors.
Copyright (c) 2023, Hopsworks AB. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You
may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. See accompanying
LICENSE file.
-->

# Quick Start

This section describes how to run YCSB on RonDB.

1. Install and Start RonDB


2. Create Table

    Create the following table in a database. YCSB will assume the database name is "ycsb" by default. This can be changed in the workload configuration file, as described below. For the fields this benchmark only supports varchar data type. 

    ```sql
    -- One 4KB data column
    CREATE TABLE usertable (YCSB_KEY VARCHAR(255) PRIMARY KEY, FIELD0 varchar(4096)) charset latin1 engine=ndbcluster;

    -- OR

    -- Ten 400B data columns
    CREATE TABLE usertable (YCSB_KEY VARCHAR(255) PRIMARY KEY, FIELD0 varchar(100), FIELD1 varchar(100), FIELD2 varchar(100), FIELD3 varchar(100), FIELD4 varchar(100), FIELD5 varchar(100), FIELD6 varchar(100), FIELD7 varchar(100), FIELD8 varchar(100), FIELD9 varchar(100)) charset latin1 engine=ndbcluster;
    ```

    *Note:* The number of columns must be equal to `fieldcount`, and the columns' length must not be less than `fieldlength`


3. Install Java and Maven

    Install Java and Maven on your platform to build the benchmark


4. Set Up YCSB

    Git clone YCSB and compile:

    ```bash
    git clone http://github.com/logicalclocks/YCSB.git 
    cd YCSB
    mvn -pl site.ycsb:rondb-binding -am clean package
    ```

5. Make sure that the RonDB native client library `libndbclient.so` is included in the `LD_LIBRARY_PATH` for this.


6. Customise workload configuration

    Specify the desired benchmark configurations using a custom or 
    pre-defined [workload file](../workloads/).   This module supports three different APIs to access RonDB. These APIs are 
   ClusterJ, REST, and gRPC. Currently, REST and  gRPC only supports read-only operations.  For 
   workloads that involve both read and write  operations, all write operations are performed 
   using clusterJ API while you can choose an API   type for read operations, for example,  
      - *rondb.read.api.type* i.e. clusterj, REST, gRPC. Default: clusterj


   ClusterJ Configuration parameters 
  
       - rondb.connection.string             Default: 127.0.0.1:1186
       - rondb.schema                        Default: ycsb

   - *REST Configurations*


      - rondb.schema                        Default: ycsb
      - rondb.api.server.ip                 Default: localhost
      - rondb.api.server.rest.port          Default: 4406
      - rondb.api.server.rest.version       Default: 0.1.0

   - *gRPC Configurations*


      - rondb.schema                        Default: ycsb
      - rondb.api.server.ip                 Default: localhost
      - rondb.api.server.grpc.port          Default: 4406
    

  - *Additional Configurations*
  
    Also, set the `fieldcount`, `fieldlength` and `fieldnameprefix` according to `usertable` schema. From the SQL examples above


      - "fieldcount=1", "fieldlength=4096", "fieldnameprefix=FIELD"
      - "fieldcount=10", "fieldlength=100", "fieldnameprefix=FIELD" 

7. Load data
   
    Currently, you can only use "ClusterJ" API for loading data into RonDB cluster. 

    ```bash
    # Use -p flag to overwrite any parameters in the specified workload file
    ./bin/ycsb load rondb -s -P workloads/workloadc \
        -p "rondb.connection.string=127.0.0.1:1186" \
        -p "rondb.schema=ycsb" \
        -p "fieldcount=10"  \
        -p "fieldlength=100"  \
        -p "fieldnameprefix=FIELD"
    ```

8. Run a workload test 

  - *ClusterJ API*

    ```bash
    # Use -p flag to overwrite any parameters in the specified workload file
    ./bin/ycsb run rondb -s -P workloads/workloadc \
        -p "rondb.connection.string=127.0.0.1:1186" \
        -p "rondb.schema=ycsb" \
        -p "rondb.read.api.type=clusterj" \
        -p "fieldcount=10"  \
        -p "fieldlength=100" \
        -p "fieldnameprefix=FIELD" \
        -p "readBatchSize=5" \
        -p "updateBatchSize=5" 
    ```
  - *REST API*

    ```bash
    # Use -p flag to overwrite any parameters in the specified workload file
    ./bin/ycsb run rondb -s -P workloads/workloadc \
        -p "rondb.connection.string=127.0.0.1:1186" \
        -p "rondb.schema=ycsb" \
        -p "rondb.read.api.type=REST" \
        -p "rondb.api.server.ip=127.0.0.1" \
        -p "rondb.api.server.rest.port=4406" \
        -p "fieldcount=10"  \
        -p "fieldlength=100" \
        -p "fieldnameprefix=FIELD" \
        -p "readBatchSize=5" \
        -p "updateBatchSize=5" 
    ```
    
  - *gRPC*

    ```bash
    # Use -p flag to overwrite any parameters in the specified workload file
    ./bin/ycsb run rondb -s -P workloads/workloadc \
        -p "rondb.connection.string=127.0.0.1:1186" \
        -p "rondb.schema=ycsb" \
        -p "rondb.read.api.type=GRPC" \
        -p "rondb.api.server.ip=127.0.0.1" \
        -p "rondb.api.server.rest.port=5406" \
        -p "fieldcount=10"  \
        -p "fieldlength=100" \
        -p "fieldnameprefix=FIELD" \
        -p "readBatchSize=5" \
        -p "updateBatchSize=5" 
    ```
