/*
 * @(#)AvgDegreeCentrality.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.*;

import core.*;

/**
 * <p>Computes the global and local centrality of a node using the average 
 * degree centrality algorithm described in <em>BUBBLE Rap: Social-based 
 * Forwarding in Delay Tolerant Networks</em> by Pan Hui et al. (2008) (the 
 * bibtex record is included below for convenience). The discussion
 * around this algorithm was a bit vague in that it describes a protocol for 
 * estimating the node's centrality by maintaining the node's degree (unique
 * past encounters). They go on to say that this value, computed for all time,
 * does not correlate to the node's centrality, but the node degree over the 
 * past six hours does. They further claim that computing this value for all
 * time is difficult and state that computing a average for multiple six hour 
 * periods in the past would be a better measure, which they do in two other 
 * algorithms, {@link CWindowCentrality} and {@link SWindowCentrality}. A third
 * algorithm called DEGREE is also presented as a computation of the node degree
 * for all time, but it's unclear whether this refers to an average over all
 * prior six-hour time windows or simply the total number of prior unique 
 * encounters. As such, both possible algorithms are implemented. 
 * AvgDegreeCentrality computes the average node degree by breaking the entire 
 * past connection history into a series of time windows, computing the node 
 * degree in each window, and computing average.<p> 
 * 
 * <p>This computation is done at regular intervals instead of every time the 
 * global and local centrality measures are requested.</p> 
 * 
 * <p>This class looks for two settings:
 * <ul>
 * <li><strong>timeWindow</strong> &ndash; the duration of each time interval 
 * (epoch) to consider. Default: 6 hours</li>
 * <li><strong>computeInterval</strong> &ndash; the amount of simulation time 
 * between updates to the centrality values. A longer interval reduces 
 * simulation time at the expense of accuracy. Default: 10 minutes</li>
 * </ul>
 * <p>
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
 * @see Centrality
 * @see DegreeCentrality
 */

/*
使用average degree centrality计算一个节点的全局和局部中心性。围绕这个算法的讨论有一点模糊因为
它这样描述了一个协议：用维护节点的度(过去相遇的不同节点)来估计该节点的中心性。他们又说这个一直计算
的值，与节点的中心性无关，却与每六个小时的度有关。他们进一步声称在所有时间计算该值很困难，并且阐述
每过去六小时的平均值是一个更好的度量方式，他们也是在两个算法中实现的(CWindowCentrality和
SWindowCentrality)。第三种称为DEGREE的算法也被提出来，该算法一直计算节点的度。但并不清楚这是
指之前所有六小时窗口的平均值还是简单的之前遇到的所有接触的总次数。本身而言，两个可能的算法都实现了。
AvgDegreeCentrality计算节点度的平均值，通过将整个历史连接信息分割成一系列的时间窗口，在每个窗
口计算节点度，以及计算平均值。
该计算在规律的时间间隔中完成，而非每次全局和局部中心性度量被请求的时候。
该类需要两项设置：窗口时间-即每个时间间隔的周期(默认6小时) 计算间隔-在更新中心性的值的仿真时间间隔。
长的计算间隔会减少仿真时间，但精度下降。默认10分钟。
*/

public class AvgDegreeCentrality implements Centrality
{
	/** Width of time window into which to group past history -setting id 
	   {@value} */
//	时间窗口的宽度，用于分类过去的历史
	public static final String CENTRALITY_WINDOW_SETTING = "timeWindow";
	/** Interval between successive updates to centrality values -setting id 
	   {@value} */
//	连续的中心性(值)更新的间隔
	public static final String COMPUTATION_INTERVAL_SETTING = "computeInterval";
	
	/** Time to wait before recomputing centrality values (node degree) */
//	重新计算中心性的等待时间
//	protected static int COMPUTE_INTERVAL = 600; // seconds, i.e. 10 minutes
	protected static int COMPUTE_INTERVAL = 60; // seconds, i.e. 1 minutes
	/** Width of each time interval in which to count the node's degree */
//	计算节点度的时间间隔
//	protected static int CENTRALITY_TIME_WINDOW = 21600; // 6 hrs, from literature
	protected static int CENTRALITY_TIME_WINDOW = 1200; // 20min , from literature
	/** Saved global centrality from last computation */
//	上次计算的全局中心性
	protected double globalCentrality;
	/** Saved local centrality from last computation */
//	上次计算的局部中心性
	protected double localCentrality;
	
	/** timestamp of last global centrality computation */
//	上次计算全局中心性的时间戳
	protected int lastGlobalComputationTime;
	/** timestamp of last local centrality computation */ 
//	上次计算局部中心性的时间戳
	protected int lastLocalComputationTime;
	
	public AvgDegreeCentrality(Settings s) 
	{
//		System.out.println("Initial for avgdegree centrality Settings s");
		if(s.contains(CENTRALITY_WINDOW_SETTING)){
			CENTRALITY_TIME_WINDOW = s.getInt(CENTRALITY_WINDOW_SETTING);
			System.out.println("CENTRALITY_TIME_WINDOW is:"+CENTRALITY_TIME_WINDOW);
		}
		
		if(s.contains(COMPUTATION_INTERVAL_SETTING)){
			COMPUTE_INTERVAL = s.getInt(COMPUTATION_INTERVAL_SETTING);
			System.out.println("COMPUTE_INTERVAL is:"+COMPUTE_INTERVAL);
		}
	}
	
	public AvgDegreeCentrality(AvgDegreeCentrality proto)
	{
//		System.out.println("Initial for avgdegree proto");
		// set these back in time (negative values) to do one computation at the 
		// start of the sim
//		将上次全局和局部中心性计算的时间戳推迟到仿真开始的时间(-600)
		this.lastGlobalComputationTime = this.lastLocalComputationTime = 
			-COMPUTE_INTERVAL;
	}
	
	public double getGlobalCentrality(Map<DTNHost, List<Duration>> connHistory)
	{
		if(SimClock.getIntTime() - this.lastGlobalComputationTime < COMPUTE_INTERVAL){
//			System.out.println("Got global cen! The global cen is:"+globalCentrality);
			return globalCentrality;
		}
		
		// initialize
//		初始化
		int epochCount = SimClock.getIntTime() / CENTRALITY_TIME_WINDOW;
//		System.out.println("SimClock.getIntTime() is:"+SimClock.getIntTime()
//				+"   CENTRALITY_TIME_WINDOW is:"+CENTRALITY_TIME_WINDOW);
//		System.out.println("epoch Count is:"+epochCount);
		int[] centralities = new int[epochCount];
		int epoch, timeNow = SimClock.getIntTime();
//		System.out.println("timeNow is:"+timeNow);
		Map<Integer, Set<DTNHost>> nodesCountedInEpoch = 
			new HashMap<Integer, Set<DTNHost>>();
		
		if(epochCount<1){
			System.out.println("the epochcount is 0!");
			return 0;
		}
		
		for(int i = 0; i < epochCount; i++){
//			System.out.println("epoch Count is executed!");
			nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
		}
		
		/*
		 * For each node, loop through connection history until we crossed all
		 * the epochs we need to cover
		 */
//		对于每个节点，循环连接历史直至我们穿越所有需要覆盖的时期
//		connHistory.entrySet().size()=0
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			DTNHost h = entry.getKey();
			for(Duration d : entry.getValue())
			{
				int timePassed = (int)(timeNow - d.end);
				
				// if we reached the end of the last epoch, we're done with this node
//				如果我们到了最后时期的末端，我们处理完了该节点
				if(timePassed > CENTRALITY_TIME_WINDOW * epochCount)
					break;
				
				// compute the epoch this contact belongs to
//				计算这个接触所属于的时期
				epoch = timePassed / CENTRALITY_TIME_WINDOW;
//				System.out.println("alert!");
				// Only consider each node once per epoch
//				每个时期仅把每个节点考虑一次
				System.out.println("nodesCountedInEpoch is:"+nodesCountedInEpoch.size());
				System.out.println("epoch is:"+epoch);
				Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
//				System.out.println("nodesAlreadyCounted is:"+nodesAlreadyCounted.size());
//				if(nodesAlreadyCounted.isEmpty())
//					continue;
				if(nodesAlreadyCounted.contains(h))
					continue;
				
				// increment the degree for the given epoch
//				对于给定的时期，增加度
				System.out.println("epoch of centralities is:"+epoch);
				System.out.println("length of centralities is:"+centralities.length);
				centralities[epoch]++;//该步没有被执行
//				System.out.println("The epoch is:"+epoch+" centralities[epoch] is:"+centralities[epoch]);
				nodesAlreadyCounted.add(h);
			}
		}
//		centralities[]中的内容都是0
//		System.out.println("The centralities[] is:"+centralities.toString());
		
		// compute and return average node degree
//		计算和返回平均节点度
		int sum = 0;
		for(int i = 0; i < epochCount; i++) 
			sum += centralities[i];
		System.out.print("The sum is:" + sum +" The epochCount is:" + epochCount);
		this.globalCentrality = ((double)sum) / epochCount;
		
		this.lastGlobalComputationTime = SimClock.getIntTime();
		System.out.println("The global Centrality is:" + this.globalCentrality);
		return this.globalCentrality;
	}

	public double getLocalCentrality(Map<DTNHost, List<Duration>> connHistory,
			CommunityDetection cd)
	{
		if(SimClock.getIntTime() - this.lastLocalComputationTime < COMPUTE_INTERVAL)
			return localCentrality;
		
		// centralities will hold the count of unique encounters in each epoch
//		中心性会保存每个时期的相遇次数
		int epochCount = SimClock.getIntTime() / CENTRALITY_TIME_WINDOW;
		int[] centralities = new int[epochCount];
		int epoch, timeNow = SimClock.getIntTime();
		Map<Integer, Set<DTNHost>> nodesCountedInEpoch = 
			new HashMap<Integer, Set<DTNHost>>();
		
		for(int i = 0; i < epochCount; i++)
			nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
		
		// local centrality only considers nodes in the local community
//		本地中心性仅考虑在本地社团的节点
		Set<DTNHost> community = cd.getLocalCommunity();
		
		/*
		 * For each node, loop through connection history until we crossed all
		 * the epochs we need to cover
		 */
//		对每个节点，循环连接历史直至我们穿越了所有需要覆盖的时期
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			DTNHost h = entry.getKey();
			
			// if the host isn't in the local community, we don't consider it
//			如果节点不再本地社团，我们不考虑它
			if(!community.contains(h))
				continue;
			
			for(Duration d : entry.getValue())
			{
				int timePassed = (int)(timeNow - d.end);
				
				// if we reached the end of the last epoch, we're done with this node
//				如果我们达到了最后时期的末端，我们处理完了该节点
				if(timePassed > CENTRALITY_TIME_WINDOW * epochCount)
					break;
				
				// compute the epoch this contact belongs to
//				计算该接触属于的时期
				epoch = timePassed / CENTRALITY_TIME_WINDOW;
				
				// Only consider each node once per epoch
//				每个时期仅把每个节点考虑一次
				Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
				if(nodesAlreadyCounted.contains(h))
					continue;
				
				// increment the degree for the given epoch
//				对于给定的时期，增加度
				centralities[epoch]++;
				nodesAlreadyCounted.add(h);
			}
		}
		
		// compute and return average node degree
//		计算和返回平均节点度
		int sum = 0;
		for(int i = 0; i < epochCount; i++) 
			sum += centralities[i];
//		System.out.print("The sum is:" + sum +" The epochCount is:" + epochCount);
		this.localCentrality = ((double)sum) / epochCount; 
		
		this.lastLocalComputationTime = SimClock.getIntTime();
		System.out.println("The local Centrality is:" + this.localCentrality);
		return this.localCentrality;
	}

	public Centrality replicate()
	{
		return new AvgDegreeCentrality(this);
	}

}
