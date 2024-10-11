import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

//Class to hold the process
public class Process {

    private boolean serverTerminate;    //status flag for server
    private boolean clientTerminate;    //status flag for client

    private int waitTime = 8000;    //time to wait to establish server sockets before starting clients

    private int masterProcessId;    //lowest processId to ensure all connections are established 
    private ArrayList<Integer> acknowledgementList = new ArrayList<>(Arrays.asList());  //list of received acknowledgements
    private ArrayList<Integer> connectionList = new ArrayList<>(Arrays.asList());  //list of processes with established connections
    private List<ConnectingProcess> connectingProcessList = new ArrayList<ConnectingProcess>(); //connection information to other processes

    private int processId;  //ID of process
    private int NUM_PROCESSES;  //num of processes
    private int NUM_MESSAGES;  //num of broadcast messages to be sent
    
    private VectorClock vectorClock;    //clock to store timestamp
    private List<Message> bufferedMessages = Collections.synchronizedList(new ArrayList<Message>());    //list of stored buffer messages
    private List<Message> deliveredMessages = Collections.synchronizedList(new ArrayList<Message>());   //list of stored delivered messages
    private List<Integer> bufferedMessagesId = new ArrayList<>();   //list of stored buffer messages' ID
    private List<Integer> deliveredMessagesId = new ArrayList<>();  //list of stored delivered messages' ID

    private List<Message> requestsList = Collections.synchronizedList(new ArrayList<Message>());  //list of requests (stored by the leader)
    private Map <Integer, ArrayList<Integer>> receivedRepliesId = new HashMap<Integer, ArrayList<Integer>>();   //HashMap with key=messageId, value=list of processes who received messageId broadcast
    private List<Integer> messagesOrder = new ArrayList<>();  //list of order of delivery of messages
    private int requestsServed; //number of requests that have been broadcasted

    private int directlyDelivered;  //count the num of messages delivered directly without buffering
    private int indirectlyDelivered; //count the num of messages buffered before delivery

    private Object obj = new Object();


    //constructor
    public Process(int processId, int NUM_PROCESSES, List<ConnectingProcess> connectingProcessList) {
        this.processId = processId;
        this.NUM_PROCESSES = NUM_PROCESSES;
        this.NUM_MESSAGES = 100;
        this.connectingProcessList = connectingProcessList;

        this.vectorClock = new VectorClock(NUM_PROCESSES, this.processId);

        this.masterProcessId = 0;
        acknowledgementList.add(processId);
        connectionList.add(processId);

        this.directlyDelivered = 0;
        this.indirectlyDelivered = 0;
        this.serverTerminate = false;
        this.clientTerminate = false;

        this.requestsServed = 0;
    }

    //get client status
    public boolean getClientTerminated() {
        return this.clientTerminate;
    }
    //get server status
    public boolean getServerTerminated() {
        return this.serverTerminate;
    }

    //establish connections with all connected processes
    public void establishConnections() throws InterruptedException {

        //start server sockets to receive messages
        this.startServerThreads();

        //broadcast an introduction message to check for connections
        Message introductionMessage = new Message("INTRODUCTION", processId, 0, null, 0);
        this.broadcastMessage(introductionMessage);
    }

    //create and start server threads to receive messages from all processes
    public synchronized void startServerThreads() throws InterruptedException {
        Thread serverThreads[] = new Thread[NUM_PROCESSES];
        for (int i = 0; i < NUM_PROCESSES; i++) {
            serverThreads[i] = getServerThread(i);  //create a listening-thread for ith process
        }
        
        for (int i = 0; i < NUM_PROCESSES; i++) {
            if(i == processId) { continue; }
            
            serverThreads[i].start();
            System.out.println("server thread for process " + i + " started");
        }

        //wait for waitTime to allow creation of clientSockets
        synchronized(obj) { obj.wait(waitTime); }
    }

    //create and return server threads to receive messages parallelly
    public Thread getServerThread(int processId) {
        Thread thread = new Thread(() -> {
            try {
                //start listen to messages incoming from processId
                this.startListening(processId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return thread;
    }


    //SERVER functions
    //function to receive all incoming messages from all processes
    public void startListening(int recevingProcessId) throws IOException, InterruptedException {
        ConnectingProcess connectingProcess = connectingProcessList.get(recevingProcessId);

        ServerSocket serverSocket = new ServerSocket(connectingProcess.port);   //creating the server-port connection

        try {
            while (!serverSocket.isClosed() || deliveredMessagesId.size() >= (NUM_PROCESSES-1)*NUM_MESSAGES) {
                Socket clientSocket = serverSocket.accept();    //accepting client-socket connections               
                try{
                    ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream());
                    Message message = (Message) inputStream.readObject();

                    if(message != null) {

                        switch (message.type) {
                            case "INTRODUCTION":
                                //send back acknowledgement to sender confirming a successful connection
                                Message acknowledgementMessage = new Message("ACKNOWLEDGEMENT", processId, message.senderProcessId, null, 0);
                                this.sendMessage(acknowledgementMessage, message.senderProcessId);
                                break;
                        
                            case "ACKNOWLEDGEMENT":
                                this.acknowledgementList.add(message.senderProcessId);  //update receiving an acknowledgement message

                                if(this.acknowledgementList.size() == NUM_PROCESSES) {  //when all acknowledgement are received                    
                                    if(processId != this.masterProcessId) {
                                        //inform the masterProcess (processId=0) that all it's connections are established
                                        Message connectMessage = new Message("CONNECTION", processId, this.masterProcessId, null, 0);
                                        this.sendMessage(connectMessage,this.masterProcessId);
                                    }
                                }
                                break;
                            
                            case "CONNECTION":
                                if(processId == this.masterProcessId) {

                                    this.connectionList.add(message.senderProcessId);   //update processes with all connections set

                                    if(this.connectionList.size() == NUM_PROCESSES) {   //when all connections are established

                                        //randomly elect a leader
                                        int electedLeader = new Random().nextInt(NUM_PROCESSES);
                                        this.masterProcessId = electedLeader;
                                        System.out.println("Elected Leader is " +  this.masterProcessId);

                                        //broadcast a LEADER message to inform everyone about the leader and start broadcasting all their messages
                                        Message leaderMessage = new Message("LEADER", processId, 0, null, electedLeader);
                                        this.broadcastMessage(leaderMessage);

                                        try {
                                            //start first-process broacasting
                                            this.runCausalBroadcast();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                break;

                            case "LEADER":
                                this.masterProcessId = message.mid;
                                System.out.println("Elected Leader is " + this.masterProcessId);
                                try {
                                    //start non-masterProcess broadcast on receiving START message from masterProcess
                                    this.runCausalBroadcast();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                break;

                            case "APPLICATION":
                                try {
                                    this.receiveMessage(message);
                                }
                                catch (Exception e) {
                                    System.out.println("couldn't receive a message");
                                    e.printStackTrace();
                                }
                                break;

                            case "REQ_MASTER":
                                //store requests incoming from all non-leader nodes
                                if(this.processId != this.masterProcessId)  break;

                                try {
                                    if(this.requestsList.size() == 0) {
                                        this.requestsList.add(message);
                                        this.sendApplicationBroadcast();    //start brodcasting messages when first request is received
                                    }
                                    else {
                                        this.requestsList.add(message);
                                    }
                                }
                                catch(InterruptedException e) {
                                    e.printStackTrace();
                                }
                                catch(Exception e) {
                                    e.printStackTrace();
                                }
                                break;
                            
                            case "APPLICATION_ACK":
                                //receive acknowledgements from non-leader nodes of delivering broadcast messages
                                if(this.processId != this.masterProcessId) break;

                                if(!this.receivedRepliesId.containsKey(message.mid))
                                this.receivedRepliesId.put(message.mid, new ArrayList<Integer>(Arrays.asList(this.masterProcessId)));   //initialize key-value pair
                                
                                //add the incoming acknowledgement in the appropriate messageId array
                                ArrayList<Integer> idList = this.receivedRepliesId.get(message.mid);
                                idList.add(message.senderProcessId);
                                this.receivedRepliesId.put(message.mid, idList);

                                if(idList.size() == this.NUM_PROCESSES) {
                                    this.requestsServed++;

                                    if(this.requestsServed >= (this.NUM_MESSAGES*this.NUM_PROCESSES))   break;

                                    this.sendApplicationBroadcast();    // broadcast the next request when all acknowledgements are received
                                }
                                break;
                            
                            default:
                                System.out.println("no such acceptable message type");
                                break;
                        }
                    }
                }
                catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            //close sockets after all messages are received
            System.out.println("Total Directly Delivered Messages: " + this.directlyDelivered);
            System.out.println("Total InDirectly Delivered Messages: " + this.indirectlyDelivered);

            serverSocket.close();

            this.serverTerminate = true;    //terminate server
        }

    }

    //function to receive a message and make a decision to buffer or deliver
    public synchronized void receiveMessage(Message message) throws InterruptedException {
        System.out.println("Received " + message.mid + " : " + message.type);

        // Emulating Network Delay for (0,5]ms
        Thread.sleep(new Random().nextInt(5)+1); //uncomment to emulate server network delay

        //deliver message if it can be, else buffer it
        if(this.canDeliverMessage(message)) {
            this.deliverMessage(message);
            this.directlyDelivered += 1;    //increment count of directly delivered messages

            messagesOrder.add(message.mid); //append the messageId in delivered messages
            System.out.println("Messages Delivery Order is: " + this.messagesOrder);
            this.sendApplicationAcknowledgement(message);   //send back acknowledgement of receiving broadcast to leader
            this.vectorClock.updateClock(message);
        }
        else {
            System.out.println("buffer message");
            this.bufferedMessagesId.add(message.mid);
            this.bufferedMessages.add(message);
            this.indirectlyDelivered += 1;  //increment count of buffered messages
        }
    }

    //function to deliver message
    public synchronized void deliverMessage(Message message) {
        System.out.println("deliver message");

        //update clock with the incoming message
        this.vectorClock.updateClock(message);

        //update list of delivered messages
        this.deliveredMessages.add(message);
        this.deliveredMessagesId.add(message.mid);

        //deliver the first message stored in buffer due to this message's late delivery
        if(this.bufferedMessages.size() > 0) {
            Message deliverableMessage = getDeliverableBufferMessage(message);

            if(deliverableMessage == null) { return; }

            //update list of buffered messages, with it's delivery
            this.bufferedMessages.remove(deliverableMessage);
            this.bufferedMessagesId.remove(this.bufferedMessagesId.indexOf(deliverableMessage.mid));

            this.deliverMessage(deliverableMessage);
        }
    }

    //check if the message can be delivered, or needs to be buffered
    public synchronized boolean canDeliverMessage(Message message) {
        boolean canDeliver = true;

        this.vectorClock.lockUnlockTime("read","lock");

        //Comparison based on message ids
        int messageId = message.mid;

        //always allow delivery of first message
        if(messageId % 100 == 1) {
            canDeliver = true;
        }
        else {
            int previousMessageId = messageId - 1;
            //allow delivery of message if immediately previous message is delivered
            if(this.deliveredMessagesId.contains(previousMessageId)) {
                canDeliver = true;
            }
            //disallow delivery of message if immediately previous message is not delivered or is in buffer
            else {
                canDeliver = false;
            }
        }

        this.vectorClock.lockUnlockTime("read","unlock");

        return canDeliver;
    }

    //return a message stored in buffer that can now be delivered, because it's immediate causal predecessor is now delivered
    public synchronized Message getDeliverableBufferMessage(Message lastDeliveredMessage) {

        int lastDeliveredMessageId = lastDeliveredMessage.mid;
        System.out.println("last delivered message Id : " + lastDeliveredMessageId);

        //sort the bufferedMessagesId to allow picking the first deliverable message
        Collections.sort(this.bufferedMessagesId);

        int deliverableMessageId = 0;
        try {
            
            //last message is delivered
            if(lastDeliveredMessageId % NUM_MESSAGES == 0) {
                //pick the earliest sent (least messageId) from the bufferedMessages from the same process
                Optional<Integer> deliverableMessageStreamId = this.bufferedMessagesId.stream().filter(id -> id > lastDeliveredMessage.senderProcessId*1000 && id < lastDeliveredMessage.senderProcessId*1000+NUM_MESSAGES).findFirst();
                try {
                    if(deliverableMessageStreamId != null) {
                        deliverableMessageId = deliverableMessageStreamId.get();
                    }
                } catch (Exception e) {
                    System.out.println("no more messages to deliver from buffer");
                }
            }
            //all messages except for last message
            else {
                //deliver immediate next messageId (of same process) if present in buffer, else return
                Optional<Integer> deliverableMessageStreamId = this.bufferedMessagesId.stream().filter(id -> id == lastDeliveredMessageId+1).findFirst();
                try {
                    if(deliverableMessageStreamId != null) {
                        deliverableMessageId = deliverableMessageStreamId.get();
                    }
                } catch (Exception e) {
                    System.out.println("no more messages to deliver from buffer");
                }
            }
            System.out.println("deliverableMessageId is " + deliverableMessageId);
            
            //return buffered message from it's ID
            final int deliverID = deliverableMessageId;
            return this.bufferedMessages.stream().filter(message -> message.mid == deliverID).findFirst().get();
        }
        catch (Exception e) {
            return null;
        }
    }


    //CLIENT functions
    //start broadcasting messages
    public void runCausalBroadcast() throws InterruptedException {

        for (int i = 0; i < NUM_MESSAGES; i++) {

            // Emulating Delay of (0,10]ms
            Thread.sleep(new Random().nextInt(10)+1);
            this.vectorClock.updateLocalClock(this.processId);  //update local time for this new broadcast event
            
            int mid = processId*1000 + i + 1;   //creating a unique message ID
            Message message = new Message("REQ_MASTER", processId, this.masterProcessId, this.vectorClock.getVectorClock(), mid);   //send a request to leader to broadcast it's message
            System.out.println("Request Master " + mid);

            if(this.processId == this.masterProcessId) {
                Thread.sleep(new Random().nextInt(5)+1);    //emulate leader's network delay of sending messages to itself

                if(this.requestsList.size() == 0) {
                    this.requestsList.add(message);
                    this.sendApplicationBroadcast();    //start brodcasting messages when first request is received
                }
                else {
                    this.requestsList.add(message);
                }
            }
            else {
                this.sendMessage(message, this.masterProcessId);    //send request message to leader
            }
        }

        this.clientTerminate = true;    //terminate client
    }

    //function to send application broadcast messages to all processes
    public synchronized void sendApplicationBroadcast() throws InterruptedException {

        if(this.requestsList.size() == 0)   return;

        Message message = this.requestsList.get(0); //process the earliest request message (top priority)

        this.vectorClock.updateClock(message);

        Message applicationMessage = new Message("APPLICATION", this.processId, message.senderProcessId, message.clock, message.mid);
        System.out.println("Send Application " + applicationMessage.mid + " to process " + message.senderProcessId);
        this.broadcastMessage(applicationMessage);  //broadcast application  message
        
        this.messagesOrder.add(applicationMessage.mid); //append delivery of message to itself
        System.out.println("Messages Delivery Order is " + this.messagesOrder);

        this.requestsList.remove(0);    //remove the currently processed request
    }

    //function to send application acknowledgement to the leader
    public synchronized void sendApplicationAcknowledgement(Message message) throws InterruptedException {
        Message acknowledgementMessage = new Message("APPLICATION_ACK", this.processId, this.masterProcessId, message.clock, message.mid);
        System.out.println("Send Acknowledgement " + acknowledgementMessage.mid + " to process " + this.masterProcessId);
        this.sendMessage(acknowledgementMessage, this.masterProcessId); //send acknowledgement message to leader
    }

    //broadcast a message to all connections
    public void broadcastMessage(Message message) {
        //array of client threads to broadcast to all other processes parallely
        Thread clientThreads[] = new Thread[NUM_PROCESSES];

        for (int i = 0; i < NUM_PROCESSES; i++) {
            if(i == processId) { continue; }    //not create for self

            clientThreads[i] = getClientThread(message, i);
        }
        for (int i = 0; i < clientThreads.length; i++) {
            if(i == processId) { continue; }    //not start for self

            clientThreads[i].start();
        }
    }

    //function to create a client thread for a single process
    public Thread getClientThread(Message message, int receiverProcessId) {
        Thread thread = new Thread(() -> {
            try {
                this.sendMessage(message, receiverProcessId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return thread;
    }

    //send a given message to the particular receiverProcessId
    public void sendMessage(Message message, int receiverProcessId) {
        int maximumTries = 5;
        int currentTries = 0;
        while(currentTries < maximumTries) {
            try {
                ConnectingProcess receiverConnectingProcess = connectingProcessList.get(receiverProcessId);
                ConnectingProcess senderConnectingProcess = connectingProcessList.get(processId);

                //client socket to connect to receiving port of receiverProcessId
                Socket socket = new Socket(receiverConnectingProcess.IP, senderConnectingProcess.port);

                //stream output the message
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                output.writeObject(message);
                output.flush();

                return;
            }
            catch (Exception e) {
                currentTries += 1;
                try {
                    Thread.sleep(1000);
                }
                catch(InterruptedException err) {
                    err.printStackTrace();
                }
            }
        }

    }

}