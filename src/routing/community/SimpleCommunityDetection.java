/*
 * @(#)SimpleCommunityDetection.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.*;

//import routing.communitydetection.DiBuBB.Duration;

import core.*;

/**
 * <p>Performs the SIMPLE Community Detection algorithm described in 
 * <em>Distributed Community Detection in Delay Tolerant Networks</em> by Pan
 * Hui et al. (bibtex record is included below for convenience). A node using
 * SIMPLE keeps a record of all the nodes it has met and the cummulative contact
 * duration it has had with each. Once this total contact duration for one of 
 * these nodes exceeds a configurable parameter, the node is added to the host's
 * familiar set and local community. When two peers meet, they compair their 
 * familiar sets and local communities for commonalities and may decide to merge
 * their local communities, which intuitively means that they've decided that 
 * they each tend to meet the same nodes often, suggesting they both are and 
 * should be part of the same local community. 
 * </p>
 * 
 * <pre>
 * \@inproceedings{1366929,
 * Address = {New York, NY, USA},
 * Author = {Hui, Pan and Yoneki, Eiko and Chan, Shu Yan and Crowcroft, Jon},
 * Booktitle = {MobiArch '07: Proceedings of 2nd ACM/IEEE international workshop
 *  on Mobility in the evolving internet architecture},
 * Doi = {http://doi.acm.org/10.1145/1366919.1366929},
 * Isbn = {978-1-59593-784-8},
 * Location = {Kyoto, Japan},
 * Pages = {1--8},
 * Publisher = {ACM},
 * Title = {Distributed community detection in delay tolerant networks},
 * Year = {2007}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 */

/*
实现SIMPLE社团发现算法
一个使用SIMPLE的节点维护一个所有相遇节点的记录以及累计接触时长。一旦这些节点中的某一个接触时长超过
一个配置的参数(阈值)，这个节点被添加到另一个节点的熟悉集和本地社团。当两个peer相遇时，他们比较各自
的熟悉集和本地社团，寻找共性，可能会导致他们的本地社团发生合并，直观来看，这意味着他们经常能够遇到对
方，也就理所当然地应该在同一社团。
*/

public class SimpleCommunityDetection implements CommunityDetection
{
	/** Threshold value for adding a host to the local community -setting id 
	 * {@value} 
	 */
//	将一个节点添加到本地社团的阈值
	public static final String LAMBDA_SETTING = "lambda";
	
	/** Threshold value for merging the local community with a peer -setting id 
	 * {@value} 
	 */
//	将本地社团与一个peer相融合的阈值
	public static final String GAMMA_SETTING = "gamma";
	
	/** Total contact time threshold for adding a node to the familiar set 
	 * -setting id {@value} 
	 */
//	将一个节点添加到熟悉集的总接触时间阈值
	public static final String FAMILIAR_SETTING = "familiarThreshold";
	
	protected Set<DTNHost> familiarSet;
	protected Set<DTNHost> localCommunity;
	
	protected double lambda;
	protected double gamma;
	protected double familiarThreshold;
	
	/**
	 * Constructs a new SimpleCommunityDetection from the Settings parameter 
	 * object 
	 * 
	 * @param s Settings from which to initialize
	 */
//	通过Settings中的参数构造一个新的SimCommunityDetection
	public SimpleCommunityDetection(Settings s)
	{
		this.lambda = s.getDouble(LAMBDA_SETTING);
		this.gamma = s.getDouble(GAMMA_SETTING);
		this.familiarThreshold = s.getDouble(FAMILIAR_SETTING);
	}
	
	public SimpleCommunityDetection(SimpleCommunityDetection proto)
	{
		this.lambda = proto.lambda;
		this.gamma = proto.gamma;
		this.familiarThreshold = proto.familiarThreshold;
		familiarSet = new HashSet<DTNHost>();
		localCommunity = new HashSet<DTNHost>();
	}
	
	public void newConnection(DTNHost myHost, DTNHost peer, 
			CommunityDetection peerCD)
	{
		boolean addPeerToMyLocal=false, addMeToPeerLocal=false;
		SimpleCommunityDetection scd = (SimpleCommunityDetection)peerCD;
		
		this.localCommunity.add(myHost);
		scd.localCommunity.add(peer);
		
		// Add peer to my local community if needed
//		如果需要的话将peer加入到本地社团
		if(!this.localCommunity.contains(peer))
		{
			/*
			 * The algorithm calls for computing the size of the intersection of 
			 * peer's familiarSet and this host's localCommunity. We divide that by
			 * the size of the peer's familiar set
			 */
//			该算法需要计算交集的大小(peer的熟悉集和本节点的本地社团)，用peer熟悉集的大小去除它
			// compute set intersection
//			计算交集
			int count=0, peerFsize = scd.familiarSet.size();
			for(DTNHost h : scd.familiarSet)
				if(this.localCommunity.contains(h))
					count++;
			
			// add peer to local community if enough nodes in common
//			若有足够多的相同节点，则将peer加入本地社团
			if(addPeerToMyLocal = ((double)count)/peerFsize > this.lambda)
			{
				this.localCommunity.add(peer);
			}
		}
		
		/*
		 * Repeat the computation for the other end of the connection
		 */
//		在连接的另一端重复一样的过程
		if(!scd.localCommunity.contains(myHost))
		{
			// compute set intersection
			int count = 0, myFsize = this.familiarSet.size();
			for(DTNHost h : this.familiarSet)
				if(scd.localCommunity.contains(h))
					count++;
			
			// add this host to local community of peer if enough nodes in common
			if(addMeToPeerLocal = ((double)count)/myFsize > scd.lambda)
			{
				scd.localCommunity.add(myHost);
			}
		}
		
		// Test for conditions when the local communities should be merged
//		当本地社团需要融合时测试条件
		if(addPeerToMyLocal || addMeToPeerLocal)
		{
			// Compute set union
//			计算并集
			Set<DTNHost> commUnion = new HashSet<DTNHost>(this.localCommunity.size() +
					scd.localCommunity.size() + 2);
			commUnion.addAll(this.localCommunity);
			commUnion.addAll(scd.localCommunity);
			
			// compute intersection of the two local communities
			// (the result is the same from both node's perspective)
//			计算两个本地社团的交集(从两个节点的角度来看是一样的)
			int count = 0;
			for(DTNHost h : this.localCommunity)
				if(scd.localCommunity.contains(h))
					count++;
			
			// merge communities if enough nodes are common
//			如果有足够多的相同节点则融合社团
			if(addPeerToMyLocal && count > this.gamma * commUnion.size())
			{
				this.localCommunity.addAll(scd.localCommunity);
			}
			if(addMeToPeerLocal && count > scd.gamma * commUnion.size())
			{
				scd.localCommunity.addAll(this.localCommunity);
			}
		}
	}
	
	public void connectionLost(DTNHost myHost, DTNHost peer, 
			CommunityDetection peerCD, List<Duration> history)
	{
		if(this.familiarSet.contains(peer)) return;
		
		/*
		 * If the peer isn't part of the familiar set, add it when the total
		 * contact duration exceeds the familiarThreshold
		 */
//		如果该peer不是熟悉集的一部分，则当全部接触时间超过熟悉阈值时，将其添加到熟悉集中
		// Compute total contact duration
//		计算全部接触时间
		Iterator<Duration> i = history.iterator();
		double time = 0;
		while(i.hasNext())
		{
			Duration d = i.next();
			time += d.end - d.start;
		}
		
		// Add peer to familiar set if needed (and by extension to the local comm.)
//		如果需要的话将peer加入到熟悉集中(甚至加入到本地社团)
		if(time > this.familiarThreshold)
		{
			this.familiarSet.add(peer);
			this.localCommunity.add(peer);
		}
	}

	public boolean isHostInCommunity(DTNHost h)
	{
		return this.localCommunity.contains(h);
	}

	public CommunityDetection replicate()
	{
		return new SimpleCommunityDetection(this);
	}

	public Set<DTNHost> getLocalCommunity()
	{
		return this.localCommunity;
	}

	public Set<DTNHost> getFamiliarSet()
	{
		return this.familiarSet;
	}
}
