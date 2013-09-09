package routing;

import java.util.*;

import core.*;

/**
 * This class overrides ActiveRouter in order to inject calls to a 
 * DecisionEngine object where needed add extract as much code from the update()
 * method as possible. 
 * 
 * <strong>Forwarding Logic:</strong> 
 * 
 * A DecisionEngineRouter maintains a List of Tuple<Message, Connection> in 
 * support of a call to ActiveRouter.tryMessagesForConnected() in 
 * DecisionEngineRouter.update(). Since update() is called so frequently, we'd 
 * like as little computation done in it as possible; hence the List that gets
 * updated when events happen. Four events cause the List to be updated: a new 
 * message from this host, a new received message, a connection goes up, or a 
 * connection goes down. On a new message (either from this host or received 
 * from a peer), the collection of open connections is examined to see if the
 * message should be forwarded along them. If so, a new Tuple is added to the
 * List. When a connection goes up, the collection of messages is examined to 
 * determine to determine if any should be sent to this new peer, adding a Tuple
 * to the list if so. When a connection goes down, any Tuple in the list
 * associated with that connection is removed from the List.
 * 
 * <strong>Decision Engines</strong>
 * 
 * Most (if not all) routing decision making is provided by a 
 * RoutingDecisionEngine object. The DecisionEngine Interface defines methods 
 * that enact computation and return decisions as follows:
 * 
 * <ul>
 *   <li>In createNewMessage(), a call to RoutingDecisionEngine.newMessage() is 
 * 	 made. A return value of true indicates that the message should be added to
 * 	 the message store for routing. A false value indicates the message should
 *   be discarded.
 *   </li>
 *   <li>changedConnection() indicates either a connection went up or down. The
 *   appropriate connectionUp() or connectionDown() method is called on the
 *   RoutingDecisionEngine object. Also, on connection up events, this first
 *   peer to call changedConnection() will also call
 *   RoutingDecisionEngine.doExchangeForNewConnection() so that the two 
 *   decision engine objects can simultaneously exchange information and update 
 *   their routing tables (without fear of this method being called a second
 *   time).
 *   </li>
 *   <li>Starting a Message transfer, a protocol first asks the neighboring peer
 *   if it's okay to send the Message. If the peer indicates that the Message is
 *   OLD or DELIVERED, call to RoutingDecisionEngine.shouldDeleteOldMessage() is
 *   made to determine if the Message should be removed from the message store.
 *   <em>Note: if tombstones are enabled or deleteDelivered is disabled, the 
 *   Message will be deleted and no call to this method will be made.</em>
 *   </li>
 *   <li>When a message is received (in messageTransferred), a call to 
 *   RoutingDecisionEngine.isFinalDest() to determine if the receiving (this) 
 *   host is an intended recipient of the Message. Next, a call to 
 *   RoutingDecisionEngine.shouldSaveReceivedMessage() is made to determine if
 *   the new message should be stored and attempts to forward it on should be
 *   made. If so, the set of Connections is examined for transfer opportunities
 *   as described above.
 *   </li>
 *   <li> When a message is sent (in transferDone()), a call to 
 *   RoutingDecisionEngine.shouldDeleteSentMessage() is made to ask if the 
 *   departed Message now residing on a peer should be removed from the message
 *   store.
 *   </li>
 * </ul>
 * 
 * <strong>Tombstones</strong>
 * 
 * The ONE has the the deleteDelivered option that lets a host delete a message
 * if it comes in contact with the message's destination. More aggressive 
 * approach lets a host remember that a given message was already delivered by
 * storing the message ID in a list of delivered messages (which is called the
 * tombstone list here). Whenever any node tries to send a message to a host 
 * that has a tombstone for the message, the sending node receives the 
 * tombstone.
 * 
 * @author PJ Dillon, University of Pittsburgh
 */

/*
该类重写了ActiveRouter目的是在需要的地方插入DecisionEngine对象的调用，以及从update()
函数中尽量抽取代码
转发逻辑：
一个DecisionEngineRouter维护了一个<消息，连接>的元组列表以支持DecisionEngineRouter.update()中
的ActiveRouter.tryMessagesForConnected()。因为update()函数调用很频繁，应在该函数中尽量
减少计算量；因此这个列表在事件发生时更新。四种事件会导致列表被更新：节点发出一个消息，节点收到一个
消息，一个连接建立，一个连接断开。对于一个新的消息（从本节点产生或从peer中收到），要检查连接的集
合看看消息是否应该发送给这些连接。如果是的话，一个新的元组被添加到列表中。当一个连接建立时，要检查
消息的集合一判断是否有消息应转发给新的peer，如果是的话向列表添加一个元组。当一个连接断开时，列表
中的任何和这个连接相关的元组都要从列表中删除。
决策引擎：
大部分（如果不是全部的话）路由决策是由RoutingDecisionEngine对象提供的。DecisionEngine接口
定义了下面的计算和返回决策。
在createNewMessage()中，调用RoutingDecisionEngine.newMessage()。true返回值表明这个
消息应该被添加到消息队列中用于路由。false返回值表示消息应该被丢弃。
changedConnection()表明一个连接建立或者断开。合适的connectionUp()或者connectionDown()
在RoutingDecsionEngine对象中调用。此外，遇到连接建立的事件时，第一个调用changedConnection()
的peer也会调用RoutingDecisionEngine.doExchangeForNewConnection()以便两个decision engine
对象能同时交换信息以及更新路由表。（不用担心该方法会被多次调用）
开始传输一个消息时，协议首先问邻居peer能否发送这个消息。如果邻居节点指出消息太老或者已经投递过了，
调用RoutingDecisionEngine.shouldDeleteOldMessage()来判断消息是否应该从消息队列中删除。
注意：如果tombstones使能或者deleteDelivered被屏蔽，消息会被删除，也不会调用该方法。
当一个消息被接收时（在messageTransfered中），调用RoutingDecisionEngine.isFinalDest()
以判断本节点是否是消息的一个接收者。接下来，调用RoutingDecisionEngine.shouldSaveReceivedMessage()
以判断新的消息是否应被储存以及是否应该试着转发。如果是的话，检查连接集合用于上面描述的机会传输。
当一个消息被发送(在transferDone())，调用RoutingDecisionEngine.shouldDeleteSentMessage()
查询离开的消息(现在peer节点上)是否应该从消息队列中删除。
ONE模拟器有deleteDelivered选项，该选项使得一个节点能在接触消息的目的节点时删除该消息。更积极的
办法是通过存储转发消息队列中的消息ID来让一个节点记住一个既定消息已被转发(在这里称为tombstone队列)。
无论何时，任何节点向一个节点(该节点已有关于该消息的tombstone)发送消息，发送节点会收到这个tombstone。
*/

public class DecisionEngineRouter extends ActiveRouter
{
	public static final String PUBSUB_NS = "DecisionEngineRouter";
	public static final String ENGINE_SETTING = "decisionEngine";
	public static final String TOMBSTONE_SETTING = "tombstones";
	public static final String CONNECTION_STATE_SETTING = "";
	
	protected boolean tombstoning;
	protected RoutingDecisionEngine decider;
	protected List<Tuple<Message, Connection>> outgoingMessages;
	
	protected Set<String> tombstones;
	
	/** 
	 * Used to save state machine when new connections are made. See comment in
	 * changedConnection() 
	 */
//	当新的连接建立时，用于保存状态机，可参考changedConnection()中的注释
	protected Map<Connection, Integer> conStates;
	
	public DecisionEngineRouter(Settings s)
	{
		super(s);
		
		Settings routeSettings = new Settings(PUBSUB_NS);
//		outgoingMessages是需要转发的<Message, Connection>元组
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
//		decider是RoutingDecisionEngine的对象
		decider = (RoutingDecisionEngine)routeSettings.createIntializedObject(
				"routing." + routeSettings.getSetting(ENGINE_SETTING));
//		TOMBSTONE_SETTING = "tombstones"
//		tombstoning is boolean
		if(routeSettings.contains(TOMBSTONE_SETTING))
			tombstoning = routeSettings.getBoolean(TOMBSTONE_SETTING);
		else
			tombstoning = false;
		
		if(tombstoning)
			tombstones = new HashSet<String>(10);
		conStates = new HashMap<Connection, Integer>(4);//conStates是Map<Connection, Integer>
	}

	public DecisionEngineRouter(DecisionEngineRouter r)
	{
		super(r);
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		decider = r.decider.replicate();
		tombstoning = r.tombstoning;
		
		if(this.tombstoning)
			tombstones = new HashSet<String>(10);
		conStates = new HashMap<Connection, Integer>(4);
	}

	//@Override
	public MessageRouter replicate()
	{
		return new DecisionEngineRouter(this);
	}

	@Override
	public boolean createNewMessage(Message m)
	{
		if(decider.newMessage(m))
		{
			if(m.getId().equals("M14"))
				System.out.println("Host: " + getHost() + "Creating M14");
			makeRoomForNewMessage(m.getSize());
			addToMessages(m, true);
			
			findConnectionsForNewMessage(m, getHost());
			return true;
		}
		return false;
	}	
	
	@Override
	public void connectionUp(Connection con)
	{
		DTNHost myHost = getHost();
		DTNHost otherNode = con.getOtherNode(myHost);
		DecisionEngineRouter otherRouter = (DecisionEngineRouter)otherNode.getRouter();
		
		decider.connectionUp(myHost, otherNode);
//		无论哪个节点调用changedConnection()，它首先要调用一次doExchange()。doExchange()
//		与DecisionEngine交互以交换信息。假设下面的代码会在两端同时使用旧的信息更新。
		if(shouldNotifyPeer(con))
		{
			this.doExchange(con, otherNode);
			otherRouter.didExchange(con);
		}
		
		/*
		 * Once we have new information computed for the peer, we figure out if
		 * there are any messages that should get sent to this peer.
		 */
//		一旦我们从peer那里获得了新的信息，我们要计算是否有任何消息需要发送给peer
		Collection<Message> msgs = getMessageCollection();
		for(Message m : msgs)
		{
			if(decider.shouldSendMessageToHost(m, otherNode))
				outgoingMessages.add(new Tuple<Message,Connection>(m, con));
		}
	}

	@Override
	public void connectionDown(Connection con)
	{
		DTNHost myHost = getHost();
		DTNHost otherNode = con.getOtherNode(myHost);
		//DecisionEngineRouter otherRouter = (DecisionEngineRouter)otherNode.getRouter();
		
		decider.connectionDown(myHost, otherNode);

		conStates.remove(con);
		
		/*
		 * If we were trying to send message to this peer, we need to remove them
		 * from the outgoing List.
		 */
//		如果我们试图向这个peer发送消息，我们需要把他们从outgoing列表中移除
		for(Iterator<Tuple<Message,Connection>> i = outgoingMessages.iterator(); 
				i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(t.getValue() == con)
				i.remove();
		}
	}
	
	protected void doExchange(Connection con, DTNHost otherHost)
	{
		conStates.put(con, 1);
		decider.doExchangeForNewConnection(con, otherHost);
	}
	
	/**
	 * Called by a peer DecisionEngineRouter to indicated that it already 
	 * performed an information exchange for the given connection.
	 * 
	 * @param con Connection on which the exchange was performed
	 */
//	peer的DecisionEngineRouter调用该方法以示它已经从给定连接更新了信息
	protected void didExchange(Connection con)
	{
		conStates.put(con, 1);
	}
	
	@Override
	protected int startTransfer(Message m, Connection con)
	{
		int retVal;
		
		if (!con.isReadyForTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		retVal = con.startTransfer(getHost(), m);
		if (retVal == RCV_OK) { // started transfer
			addToSendingConnections(con);
		}
		else if(tombstoning && retVal == DENIED_DELIVERED)		//该判断逻辑是添加的内容
		{
			this.deleteMessage(m.getId(), false);
			tombstones.add(m.getId());
		}
		else if (deleteDelivered && (retVal == DENIED_OLD || retVal == DENIED_DELIVERED) && //改变的内容
				decider.shouldDeleteOldMessage(m, con.getOtherNode(getHost()))) {			 //改变的内容
			/* final recipient has already received the msg -> delete it */
//			如果最终接收者已经收到了消息，那么删除消息
			if(m.getId().equals("M14"))		//添加的内容
				System.out.println("Host: " + getHost() + " told to delete M14");  //添加的内容
			this.deleteMessage(m.getId(), false);
		}
		
		return retVal;
	}

	@Override
	public int receiveMessage(Message m, DTNHost from)
	{
		if(isDeliveredMessage(m) || (tombstoning && tombstones.contains(m.getId())))
			return DENIED_DELIVERED;
			
		return super.receiveMessage(m, from);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message incoming = removeFromIncomingBuffer(id, from);
	
		if (incoming == null) {
			throw new SimError("No message with ID " + id + " in the incoming "+
					"buffer of " + getHost());
		}
		
		incoming.setReceiveTime(SimClock.getTime());
		
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, getHost());
			if (outgoing == null) break; // Some app wanted to drop the message
		}
		
		Message aMessage = (outgoing==null)?(incoming):(outgoing);
//		改变的内容，messageRouter中的内容是：isFinalRecipient = aMessage.getTo() == this.host;
		boolean isFinalRecipient = decider.isFinalDest(aMessage, getHost());
		boolean isFirstDelivery =  isFinalRecipient && 
			!isDeliveredMessage(aMessage);
//		改变的内容，messageRouter中的内容是：if (!isFinalRecipient && outgoing!=null) 
		if (outgoing!=null && decider.shouldSaveReceivedMessage(aMessage, getHost())) 
		{
			// not the final recipient and app doesn't want to drop the message
			// -> put to buffer
			addToMessages(aMessage, false);
			
			// Determine any other connections to which to forward a message
			findConnectionsForNewMessage(aMessage, from);	//添加的内容
		}
		
		if (isFirstDelivery)
		{
			this.deliveredMessages.put(id, aMessage);
		}
		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, from, getHost(),
					isFirstDelivery);
		}
		
		return aMessage;
	}

	@Override
	protected void transferDone(Connection con)
	{
		Message transferred = this.getMessage(con.getMessage().getId());
		if(transferred==null){
			System.out.println("transferred is null!");
			return;
		}
//		删除不存在的<Message, Connection>元组
		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
		i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			
			if(transferred==null){
				System.out.println("transferred is null!");
			}
			
			System.out.println("id of transferred is:"+transferred.getId());
			
			if(t.getKey().getId().equals(transferred.getId()) && 
					t.getValue().equals(con))
			{
				i.remove();
				break;
			}
		}
//		当一个message发送到peer时，判断这个message是否需要在本节点删除
		if(decider.shouldDeleteSentMessage(transferred, con.getOtherNode(getHost())))
		{
			if(transferred.getId().equals("M14"))
				System.out.println("Host: " + getHost() + " deleting M14 after transfer");
			this.deleteMessage(transferred.getId(), false);
//			删除不存在的<Message, Connection>元组
			for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
			i.hasNext();)
			{
				Tuple<Message, Connection> t = i.next();
				if(t.getKey().getId().equals(transferred.getId()))
				{
					i.remove();
				}
			}
		}
	}

	@Override
	public void update()
	{
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		// Try only the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer
		}
		
		tryMessagesForConnected(outgoingMessages);
//		删除不存在的<Message, Connection>元组
		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
			i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(!this.hasMessage(t.getKey().getId()))
			{
				i.remove();
			}
		}
	}
	
	public RoutingDecisionEngine getDecisionEngine()
	{
		return this.decider;
	}

	protected boolean shouldNotifyPeer(Connection con)
	{
		Integer i = conStates.get(con);
		return i == null || i < 1;
	}
	
	protected void findConnectionsForNewMessage(Message m, DTNHost from)
	{
		for(Connection c : getHost()) 
		{
			DTNHost other = c.getOtherNode(getHost());
			if(other != from && decider.shouldSendMessageToHost(m, other))
			{
				if(m.getId().equals("M14"))
					System.out.println("Adding attempt for M14 from: " + getHost() + " to: " + other);
				outgoingMessages.add(new Tuple<Message, Connection>(m, c));
			}
		}
	}
}
