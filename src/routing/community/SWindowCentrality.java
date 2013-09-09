/*
 * @(#)SWindowCentrality.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.List;
import java.util.Map;

import core.*;

/**
 * <p>Computes the centrality of a node using the SWindow centrality algorithm 
 * described in <em>BUBBLE Rap: Social-based Forwarding in Delay Tolerant 
 * Networks</em> by Pan Hui et al. (2008) (the bibtex
 * record is included below for convenience).</p> 
 * 
 * <p>SWindow estimates the node's centrality using the node's degree over the 
 * most recent time window, i.e. a count of the unique encounters made over the
 * last few hours. The authors found that a six hour time window correlated well 
 * with a node's actual centrality value (for the datasets they examined), which
 * is set to be the default interal but can be configured in the settings file.
 * </p>
 * 
 * <p>This class looks for two settings:
 * <ul>
 * <li><strong>timeWindow</strong> &ndash; the duration of the time interval 
 * (epoch) to consider. Default: 6 hours</li>
 * <li><strong>computeInterval</strong> &ndash; the amount of simulation time 
 * between updates to the centrality values. A longer interval reduces 
 * simulation time at the expense of accuracy. Default: 10 minutes</li>
 * </ul>
 * </p>
 * 
 * <pre>
 * \@inproceedings{1374652,
 *	Address = {New York, NY, USA},
 *	Author = {Hui, Pan and Crowcroft, Jon and Yoneki, Eiko},
 *	Booktitle = {MobiHoc '08: Proceedings of the 9th ACM international symposium 
 *		on Mobile ad hoc networking and computing},
 *	Doi = {http://doi.acm.org/10.1145/1374618.1374652},
 *	Isbn = {978-1-60558-073-9},
 *	Location = {Hong Kong, Hong Kong, China},
 *	Pages = {241--250},
 *	Publisher = {ACM},
 *	Title = {BUBBLE Rap: Social-based Forwarding in Delay Tolerant Networks},
 *	Url = {http://portal.acm.org/ft_gateway.cfm?id=1374652&type=pdf&coll=GUIDE&dl=GUIDE&CFID=55195392&CFTOKEN=93998863},
 *	Year = {2008}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 */

/*
使用SWindow中心性算法计算一个节点的中心性。
SWindow使用最近的时间窗口(即最近几小时接触的记录)的节点度计算节点的中心性。作者发现6小时的时间窗
口与一个节点的实际中心度关联得最好(通过检查数据集)，6小时在配置文件中设成默认值。
该类需要两项配置：
时间窗口：时间间隔(时期)的长度，默认6小时
计算间隔：更新中心度的值的仿真时间间隔。长的时间间隔会减少仿真时间，同时精度也会下降，默认时间10分钟。
*/

public class SWindowCentrality implements Centrality
{
	/** length of time into the past to consider -setting id {@value} */
//	用于从配置文件中读取内容
	public static final String CENTRALITY_WINDOW_SETTING = "timeWindow";
	/** time interval between successive updates to centrality value -setting id 
	 			{@value} */
	public static final String COMPUTATION_INTERVAL_SETTING = "computeInterval";
	
	/** Time to wait before recomputing centrality values (node degree) */
//	重新计算中心度的等待时间(周期)
	protected static int COMPUTE_INTERVAL = 600; // seconds, i.e. 10 minutes
	/** Width of each time interval in which to count the node's degree */
//	计算节点度的时间间隔
	protected static int CENTRALITY_TIME_WINDOW = 21600; // 6 hours
	
	/** Saved global centrality from last computation */
//	上次计算保留的全局中心度
	protected double globalCentrality;
	/** Saved local centrality from last computation */
//	上次计算保留的局部中心度
	protected double localCentrality;
	
	/** timestamp of last global centrality computation */
//	上次全局中心度计算的时间戳
	protected int lastGlobalComputationTime;
	/** timestamp of last local centrality computation */ 
//	上次局部中心度计算的时间戳
	protected int lastLocalComputationTime;
	
	public SWindowCentrality(Settings s)
	{
		if(s.contains(CENTRALITY_WINDOW_SETTING))
			CENTRALITY_TIME_WINDOW = s.getInt(CENTRALITY_WINDOW_SETTING);
	}
	
	public SWindowCentrality(SWindowCentrality proto)
	{
		// set these back in time (negative values) to do one computation at the 
		// start of the sim
//		将全局计算时间和本地计算时间设置为初始值(-600)
		this.lastGlobalComputationTime = this.lastLocalComputationTime = 
				-COMPUTE_INTERVAL;
	}
	
	public double getGlobalCentrality(Map<DTNHost, List<Duration>> connHistory)
	{
		if(SimClock.getIntTime() - this.lastGlobalComputationTime <COMPUTE_INTERVAL)
			return globalCentrality;
		
		int centrality = 0;
		int timeNow = SimClock.getIntTime();
		
		// For each host, check whether its last contact time was within window
		// no need to loop through the entire contact history
//		对于每个节点，检查上次接触时间是否在窗口中，没有必要检查整个历史接触信息
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			List<Duration> l = entry.getValue();
			if(timeNow - l.get(l.size()-1).end < CENTRALITY_TIME_WINDOW)
				centrality++;
		}
		
		this.lastGlobalComputationTime = SimClock.getIntTime();
		return this.globalCentrality = centrality;
	}

	public double getLocalCentrality(Map<DTNHost, List<Duration>> connHistory, 
			CommunityDetection cd)
	{
		if(SimClock.getIntTime() - this.lastLocalComputationTime < COMPUTE_INTERVAL)
			return localCentrality;
		
		int centrality = 0;
		int timeNow = SimClock.getIntTime();
		
		// same check as for global centrality, but must ensure host is in local
		// community too
//		和全局中心度的检查一样，但必须保证节点在本地社团
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			List<Duration> l = entry.getValue();
			if(timeNow - l.get(l.size() - 1).end > CENTRALITY_TIME_WINDOW)
				break;
			if(cd.isHostInCommunity(entry.getKey()))
				centrality++;
		}
		
		this.lastLocalComputationTime = SimClock.getIntTime();
		return this.localCentrality = centrality;
	}

	public Centrality replicate()
	{
		return new SWindowCentrality(this);
	}
}
