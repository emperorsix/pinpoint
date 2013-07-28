package com.nhn.pinpoint.collector.dao.hbase;

import static com.nhn.pinpoint.common.hbase.HBaseTables.APPLICATION_STATISTICS;
import static com.nhn.pinpoint.common.hbase.HBaseTables.APPLICATION_STATISTICS_CF_COUNTER;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.nhn.pinpoint.collector.dao.ApplicationStatisticsDao;
import com.nhn.pinpoint.collector.dao.hbase.StatisticsCache.Value;
import com.nhn.pinpoint.collector.util.AcceptedTimeService;
import com.nhn.pinpoint.common.ServiceType;
import com.nhn.pinpoint.common.hbase.HbaseOperations2;
import com.nhn.pinpoint.common.util.ApplicationStatisticsUtils;
import com.nhn.pinpoint.common.util.TimeSlot;

/**
 * appllication 통계
 * 
 * @author netspider
 */
public class HbaseApplicationStatisticsDao implements ApplicationStatisticsDao {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private HbaseOperations2 hbaseTemplate;

	@Autowired
	private AcceptedTimeService acceptedTimeService;

	private final boolean useBulk;
	private final StatisticsCache cache;

	public HbaseApplicationStatisticsDao() {
		this.useBulk = false;
		this.cache = null;
	}

	public HbaseApplicationStatisticsDao(boolean useBulk) {
		this.useBulk = useBulk;
		this.cache = (useBulk) ? new StatisticsCache() : null;
	}
	
	@Override
	public void update(String applicationName, short serviceType, String agentId, int elapsed, boolean isError) {
		if (applicationName == null) {
			throw new IllegalArgumentException("applicationName is null.");
		}

		if (logger.isDebugEnabled()) {
			logger.debug("[UpdatingApplicationMapStatistics] " + applicationName + ", " + ServiceType.findServiceType(serviceType) + ", " + agentId + ", " + elapsed + ", " + isError);
		}

		// make row key. rowkey는 나.
		long acceptedTime = acceptedTimeService.getAcceptedTime();
		long rowTimeSlot = TimeSlot.getStatisticsRowSlot(acceptedTime);
		final byte[] rowKey = ApplicationStatisticsUtils.makeRowKey(applicationName, serviceType, rowTimeSlot);

		byte[] columnName = ApplicationStatisticsUtils.makeColumnName(serviceType, agentId, elapsed, isError);

		if (useBulk) {
			cache.add(rowKey, columnName, 1L);
		} else {
			hbaseTemplate.incrementColumnValue(APPLICATION_STATISTICS, rowKey, APPLICATION_STATISTICS_CF_COUNTER, columnName, 1L);
		}
	}

	@Override
	public void flush() {
		if (!useBulk) {
			throw new IllegalStateException();
		}

		List<Value> itemList = cache.getItems();
		for (Value item : itemList) {
			hbaseTemplate.incrementColumnValue(APPLICATION_STATISTICS, item.getRowKey(), APPLICATION_STATISTICS_CF_COUNTER, item.getColumnName(), item.getLongValue());
		}
	}

	@Override
	public void flushAll() {
		if (!useBulk) {
			throw new IllegalStateException();
		}

		List<Value> itemList = cache.getAllItems();
		for (Value item : itemList) {
			hbaseTemplate.incrementColumnValue(APPLICATION_STATISTICS, item.getRowKey(), APPLICATION_STATISTICS_CF_COUNTER, item.getColumnName(), item.getLongValue());
		}
	}
}
