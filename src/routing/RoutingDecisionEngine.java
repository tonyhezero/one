package routing;

import core.*;

/**
 * Defines the interface between DecisionEngineRouter and its decision making
 * object. 
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public interface RoutingDecisionEngine
{
	/**
	 * Called when a connection goes up between this host and a peer. Note that,
	 * doExchangeForNewConnection() may be called first.
	 * 
	 * @param thisHost
	 * @param peer
	 */
//	当连接在两者之间建立时，调用该函数，注意：doExchangeForNewConnection()或许被先调用
	public void connectionUp(DTNHost thisHost, DTNHost peer);
	
	/**
	 * Called when a connection goes down between this host and a peer.
	 * 
	 * @param thisHost
	 * @param peer
	 */
//	当两者之间的连接断开的时候，调用该函数
	public void connectionDown(DTNHost thisHost, DTNHost peer);
	
	/**
	 * Called once for each connection that comes up to give two decision engine
	 * objects on either end of the connection to exchange and update information
	 * in a simultaneous fashion. This call is provided so that one end of the 
	 * connection does not perform an update based on newly updated information 
	 * from the opposite end of the connection (real life would reflect an update
	 * based on the old peer information). 
	 * 
	 * @param con
	 * @param peer
	 */
//	每个连接建立时给连接两端的转发引擎对象同时交换和更新信息，每个连接调用一次
//	该函数的作用是使连接一端不用根据新的更新信息而执行更新
	public void doExchangeForNewConnection(Connection con, DTNHost peer);
	
	/**
	 * Allows the decision engine to gather information from the given message and
	 * determine if it should be forwarded on or discarded. This method is only 
	 * called when a message originates at the current host (not when received 
	 * from a peer). In this way, applications can use a Message to communicate
	 * information to this routing layer.
	 * 
	 * @param m the new Message to consider routing
	 * @return True if the message should be forwarded on. False if the message 
	 * should be discarded.  
	 */
//	允许转发引擎从给定消息中收集信息以及决定该小时是否应该被转发或者丢弃。该方法仅在本地节点产生
//	消息时才调用（而不是从peer收到的消息）。这样，应用程序能够使用一个消息与该路由层转达消息
	public boolean newMessage(Message m);
	
	/**
	 * Determines if the given host is an intended recipient of the given Message.
	 * This method is expected to be called when a new Message is received at a
	 * given router. 
	 * 
	 * @param m Message just received
	 * @param aHost Host to check
	 * @return true if the given host is a recipient of this given message. False
	 * otherwise.
	 */
//	判定一个给定的节点是否是消息的目的节点；该方法预计在一个新的消息被路由器接收到的时候调用
//	如何给定节点是接受方的话就返回true，否则返回false
	public boolean isFinalDest(Message m, DTNHost aHost);
	
	/**
	 * Called to determine if a new message received from a peer should be saved
	 * to the host's message store and further forwarded on.
	 * 
	 * @param m Message just received
	 * @param thisHost The requesting host
	 * @return true if the message should be saved and further routed. 
	 * False otherwise.
	 */
//	调用该函数判断从peer接收到的新的消息是否应该添加到节点的消息队列中以及将来转发该消息
//	如果消息应该被保存和将来被转发则返回true，否则返回false
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost);
	
	/**
	 * Called to determine if the given Message should be sent to the given host.
	 * This method will often be called multiple times in succession as the
	 * DecisionEngineRouter loops through its respective Message or Connection
	 * Collections.  
	 * 
	 * @param m Message to possibly sent
	 * @param otherHost peer to potentially send the message to.
	 * @return true if the message should be sent. False otherwise.
	 */
//	判断一个消息是否应该被发送给这个就节点，这个方法常常被连续调用多次，因为转发引擎路由器会通过
//	各自的消息集合和连接集合循环
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost);
	
	/**
	 * Called after a message is sent to some other peer to ask if it should now
	 * be deleted from the message store. 
	 * 
	 * @param m Sent message
	 * @param otherHost Host who received the message
	 * @return true if the message should be deleted. False otherwise.
	 */
//	当一个消息转发到其他peer时调用，用于询问是否应该从消息队列中删除
//	如果应该删除返回true，否则返回false
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost);
	
	/**
	 * Called if an attempt was unsuccessfully made to transfer a message to a 
	 * peer and the return code indicates the message is old or already delivered,
	 * in which case it might be appropriate to delete the message. 
	 * 
	 * @param m Old Message
	 * @param hostReportingOld Peer claiming the message is old
	 * @return true if the message should be deleted. False otherwise.
	 */
//	当一个消息没有顺利传递到一个peer时调用该函数，返回值会指出消息太老或是已经投递了，在这种情况
//	下，应该删除消息
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld);
	
	/**
	 * Duplicates this decision engine.
	 * 
	 * @return
	 */
	public RoutingDecisionEngine replicate();
}
