/*
 * @(#)KCLiqueCommunityDetection.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 */
package routing.community;

import java.util.*;

//import routing.communitydetection.DiBuBB.Duration;

import core.*;

/**
 * <p>Performs the K-Clique Community Detection algorithm described in 
 * <em>Distributed Community Detection in Delay Tolerant Networks</em> by Pan
 * Hui et al. (bibtex record is included below for convenience). A node using
 * K-Clique keeps a record of all the nodes it has met and the cummulative 
 * contact duration it has had with each. Once this total contact duration for 
 * one of these nodes exceeds a configurable parameter, the node is added to the 
 * host's familiar set and local community and the node's familiar set is added 
 * to an approximation of all the familiar sets of the host's local community.  
 * </p>
 * <p>Note: In ONE, each KCliqueCommunityDetection stores a reference to another 
 * node's familiar set instead of creating and managing a duplicate of it. 
 * </p>
 * <p>When two peers meet, they exchange familiar sets, local community sets, 
 * and their respective approximations of the familiar sets of their local 
 * communities. If the nodes are not part of each other's local communities, 
 * the set intersection between the local community of one host and the familiar
 * set of its peer is computed. If the size of this intersection is 
 * greater than the configurable parameter, <code>K</code>, the peer has K nodes
 * in common with the local community and should therefore be added to the local
 * community. In this case, then, the peer's local community may have other 
 * nodes that also share K nodes in common with the local community, which then
 * should be added to it as well. 
 * </p>
 * <pre>
 * \@inproceedings{1366929,
 * Address = {New York, NY, USA},
 * Author = {Hui, Pan and Yoneki, Eiko and Chan, Shu Yan and Crowcroft, Jon},
 * Booktitle = {MobiArch '07: Proceedings of 2nd ACM/IEEE international workshop 
 * 	on Mobility in the evolving internet architecture},
 * Doi = {http://doi.acm.org/10.1145/1366919.1366929},
 * Isbn = {978-1-59593-784-8},
 * Location = {Kyoto, Japan},
 * Pages = {1--8},
 * Publisher = {ACM},
 * Title = {Distributed Community Detection in Delay Tolerant Networks},
 * Year = {2007}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 */

/*
实现K-Clique社团检测算法。使用K-Clique算法的节点维护一个所有相遇节点的记录以及累计接触时长。一旦
这些节点中的某一个接触时长超过一个配置的参数(阈值)，这个节点被添加到本地节点的熟悉集和本地社团。并且
这个节点的熟悉集被添加到本地节点的本地社团的熟悉集的近似。
注意：在one中，每个KCliqueCommunityDetection保存了另一个节点熟悉集的引用，而非创建和管理熟悉
集的一个复制品。
当两个节点相遇时，他们交换熟悉集，本地社团集合，以及他们各自的本地社团熟悉集的近似。如果这两个节点
不是对方本地社团的一部分，那么一个节点的本地社团和它的peer的熟悉集的交集会被算出来。如果这个交集的
大小超过了阈值K，这个peer与本地社团有K个共同的节点，那么peer应该被加入到本地社团。在这种情况下，
这个peer的本地社团也许会与本地社团有K个共同的节点，那么也应该将本地节点添加到peer的本地社团中。
*/

public class KCliqueCommunityDetection implements CommunityDetection
{
	public static final String K_SETTING = "K";
	public static final String FAMILIAR_SETTING = "familiarThreshold";
	
	protected Set<DTNHost> familiarSet;
	protected Set<DTNHost> localCommunity;
	protected Map<DTNHost, Set<DTNHost>> familiarsOfMyCommunity;
	
	protected double k;
	protected double familiarThreshold;
	
	public KCliqueCommunityDetection(Settings s)
	{
		this.k = s.getDouble(K_SETTING);
		this.familiarThreshold = s.getDouble(FAMILIAR_SETTING);
	}
	
	public KCliqueCommunityDetection(KCliqueCommunityDetection proto)
	{
		this.k = proto.k;
		this.familiarThreshold = proto.familiarThreshold;
		familiarSet = new HashSet<DTNHost>();
		localCommunity = new HashSet<DTNHost>();
		this.familiarsOfMyCommunity = new HashMap<DTNHost, Set<DTNHost>>();
	}
	
	public void newConnection(DTNHost myHost, DTNHost peer, 
			CommunityDetection peerCD)
	{
		KCliqueCommunityDetection scd = (KCliqueCommunityDetection)peerCD;
		
		// Ensure each node is in its own local community
		// (This is the first instance where we actually get the host for these 
		// objects)
//		确保每个节点在自己的本地社团中，这是我们为这写对象准备节点的第一次实例化
		this.localCommunity.add(myHost);
		scd.localCommunity.add(peer);
		
		/*
		 * The first few steps of the protocol are
		 *  (1) update my local approximation of my peer's familiar set
		 *  (2) merge my and my peer's local approximations of our respective
		 *      community's familiar sets
		 * 
		 * In both these cases, for ONE, each CommunityDetection object stores a 
		 * reference to the familiar set of its community members. As those members
		 * update their familiar set, others storing a reference to that set
		 * immediately witness the reflected changes. Therefore, we don't have to 
		 * anything to update an "approximation" of the familiar sets. They're not
		 * approximations here anymore. In this way, what we have in the k-Clique
		 * community detection class is an upper bound on the performance of the
		 * protocol.
		 */
//		协议的头几部设计是这样的：
//		(1)更新peer熟悉集的本地近似
//		(2)将我和我peer的各自社团熟悉集的近似相融合
//		在这两中情况下，对于ONE来讲，每个CommunityDetection对象保存了他们社团成员熟悉集的引用。
//		当这些成员更新各自的熟悉集时，存储那个集合引用的节点会马上见证这些变化。因此，我们不再有任
//		何东西可以更新这些熟悉集的一个”近似“。他们也不再是近似了。这样，我们在K-Clique社团检测
//		的类中所拥有的是协议性能的一个上界。
		// Add peer to my local community if needed
//		如果需要的话将peer加入到我的本地社团
		if(!this.localCommunity.contains(peer))
		{
			// compute the intersection size
//			计算交集的大小
			int count=0;
			for(DTNHost h : scd.familiarSet)
				if(this.localCommunity.contains(h))
					count++;
			
			// if peer familiar has K nodes in common with this host's local community
//			如果peer的熟悉集与本地节点的本地社团有K个共同节点
			if(count >= this.k - 1)
			{
				this.localCommunity.add(peer);
				this.familiarsOfMyCommunity.put(peer, scd.familiarSet);
				
				// search the peer's local community for other nodes with K in common
				// (like a transitivity property)
//				搜索peer的本地社团以寻找拥有K个共同节点的其他节点(就像传递性)
				for(DTNHost h : scd.localCommunity)
				{
					if(h == myHost || h == peer) continue;
					
					// compute intersection size
//					计算交集大小
					count = 0;
					for(DTNHost i : scd.familiarsOfMyCommunity.get(h))
						if(this.localCommunity.contains(i))
							count++;
					
					// add nodes if there are K in common with this local community
//					如果与本地社团有K个共同节点，则添加节点
					if(count >= this.k - 1)
					{
						this.localCommunity.add(h);
						this.familiarsOfMyCommunity.put(h, 
								scd.familiarsOfMyCommunity.get(h));
					}
				}
			}
		}
		
		// Repeat process from peer's perspective
//		从peer的角度出发重复上面的过程
		if(!scd.localCommunity.contains(myHost))
		{
			int count = 0;
			for(DTNHost h : this.familiarSet)
				if(scd.localCommunity.contains(h))
					count++;
			if(count >= scd.k - 1)
			{
				scd.localCommunity.add(myHost);
				scd.familiarsOfMyCommunity.put(myHost, this.familiarSet);
				
				for(DTNHost h : this.localCommunity)
				{
					if(h == myHost || h == peer) continue;
					count = 0;
					for(DTNHost i : this.familiarsOfMyCommunity.get(h))
						if(scd.localCommunity.contains(i))
							count++;
					if(count >= scd.k - 1)
					{
						scd.localCommunity.add(h);
						scd.familiarsOfMyCommunity.put(h, 
								this.familiarsOfMyCommunity.get(h));
					}
				}
			}
		}
	}
	
	public void connectionLost(DTNHost myHost, DTNHost peer, 
			CommunityDetection peerCD, List<Duration> history)
	{
		if(this.familiarSet.contains(peer)) return;
		
		// Compute cummulative contact duration with this peer
//		计算与该peer的累计接触时长
		Iterator<Duration> i = history.iterator();
		double time = 0;
		while(i.hasNext())
		{
			Duration d = i.next();
			time += d.end - d.start;
		}
		
		// If cummulative duration is greater than threshold, add
//		如果累计接触时长大于阈值，添加
		if(time > this.familiarThreshold)
		{
			KCliqueCommunityDetection scd = (KCliqueCommunityDetection)peerCD;
			this.familiarSet.add(peer);
			this.localCommunity.add(peer);
			this.familiarsOfMyCommunity.put(peer, scd.familiarSet);
		}
	}

	public boolean isHostInCommunity(DTNHost h)
	{
		return this.localCommunity.contains(h);
	}

	public CommunityDetection replicate()
	{
		return new KCliqueCommunityDetection(this);
	}

	public Set<DTNHost> getLocalCommunity()
	{
		return this.localCommunity;
	}
	
}
