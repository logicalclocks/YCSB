/**
 * Copyright (c) 2011 YCSB++ project, 2014-2023 YCSB contributors.
 * Copyright (c) 2023, Hopsworks AB. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.measurements;

import site.ycsb.measurements.exporter.MeasurementsExporter;
import site.ycsb.workloads.CoreWorkload;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Properties;
import java.util.Vector;

class SeriesUnit {
  /**
   * @param time
   * @param average
   */
  public SeriesUnit(long time, double average) {
    this.time = time;
    this.average = average;
  }

  protected final long time;
  protected final double average;
}

/**
 * A time series measurement of a metric, such as READ LATENCY.
 */
public class OneMeasurementTimeSeries extends OneMeasurement {

  /**
   * Granularity for time series; measurements will be averaged in chunks of this granularity. Units are milliseconds.
   */
  public static final String GRANULARITY = "timeseries.granularity";
  public static final String GRANULARITY_DEFAULT = "1000";

  private final int granularity;
  private final Vector<SeriesUnit> measurements;

  private long start = -1;
  private long currentunit = -1;
  private long count = 0;
  private long sum = 0;
  private long operations = 0;
  private long totallatency = 0;

  //keep a windowed version of these stats for printing status
  private int windowoperations = 0;
  private long windowtotallatency = 0;

  private int min = -1;
  private int max = -1;
  private final int readBatchSize;
  private final int updateBatchSize;

  public OneMeasurementTimeSeries(String name, Properties props) {
    super(name);
    granularity = Integer.parseInt(props.getProperty(GRANULARITY, GRANULARITY_DEFAULT));
    measurements = new Vector<>();
    readBatchSize = Integer.valueOf(props.getProperty(CoreWorkload.READ_BATCH_SIZE_PROPERTY,
          CoreWorkload.READ_BATCH_SIZE_PROPERTY_DEFAULT));
    updateBatchSize = Integer.valueOf(props.getProperty(CoreWorkload.UPDATE_BATCH_SIZE_PROPERTY,
        CoreWorkload.UPDATE_BATCH_SIZE_PROPERTY_DEFAULT));
  }

  private synchronized void checkEndOfUnit(boolean forceend) {
    long now = System.currentTimeMillis();

    if (start < 0) {
      currentunit = 0;
      start = now;
    }

    long unit = ((now - start) / granularity) * granularity;

    if ((unit > currentunit) || (forceend)) {
      double avg = ((double) sum) / ((double) count);
      measurements.add(new SeriesUnit(currentunit, avg));

      currentunit = unit;

      count = 0;
      sum = 0;
    }
  }

  @Override
  public void measure(int latency) {
    checkEndOfUnit(false);

    count++;
    sum += latency;
    totallatency += latency;
    operations++;
    windowoperations++;
    windowtotallatency += latency;

    if (latency > max) {
      max = latency;
    }

    if ((latency < min) || (min < 0)) {
      min = latency;
    }
  }


  @Override
  public void exportMeasurements(MeasurementsExporter exporter) throws IOException {
    checkEndOfUnit(true);

    String prepend = "";
    if (getName().compareTo("BATCH_READ") == 0) {
      exporter.write(getName(), "BatchSize", readBatchSize);
      prepend = "Batch";
    }

    if (getName().compareTo("BATCH_UPDATE") == 0) {
      exporter.write(getName(), "BatchSize", updateBatchSize);
      prepend = "Batch";
    }
    exporter.write(getName(), prepend+"Operations", operations);
    exporter.write(getName(), prepend+"AverageLatency(us)", (((double) totallatency) / ((double) operations)));
    exporter.write(getName(), prepend+"MinLatency(us)", min);
    exporter.write(getName(), prepend+"MaxLatency(us)", max);

    // TODO: 95th and 99th percentile latency

    exportStatusCounts(exporter);
    for (SeriesUnit unit : measurements) {
      exporter.write(getName(), Long.toString(unit.time), unit.average);
    }
  }

  @Override
  public String getSummary() {
    if (windowoperations == 0) {
      return "";
    }
    DecimalFormat d = new DecimalFormat("#.##");
    double report = ((double) windowtotallatency) / ((double) windowoperations);
    windowtotallatency = 0;
    windowoperations = 0;
    return "[" + getName() + " AverageLatency(us)=" + d.format(report) + "]";
  }

}
