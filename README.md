## Totally Ordered Broadcast

This project implements Totally Ordered Broadcast of messages among 4 processes running on different machines on a network.
The 4 machines are completely connected to each other.

How the Algorithm works:

A leader among the the 4 proceses is elected (randomly).

All processes send their message requests to the leader. The leader processes the requests in order, and sends the respective message to all processes.
Each receiving process sends back an acknowledgement to the leader. When the leader has received all acknowledgements (4 in this case), it updates it's request queue and moves on to broadcast the next one, until all requests have been served.

100 such requests are made by each process. And thus a total of 300 broadcast messages from others and 100 from self are processed.

Paper Cited:

Défago, Xavier & Schiper, André & Urbán, Péter. (2000). Totally Ordered Broadcast and Multicast Algorithms: A Comprehensive Survey.

This paper discusses about the families of algorithms which ensure total ordering, fault-tolerance, reliability in distributed systems. It is a comprehensive survey on several algorithms, including those about atomic broadcasts (to ensure total ordering of messages).


To run the project:

1. Mount the folder on any of the dc20-dc23 machines, using:
    ``` shell
    scp -r <sourceFolderLocation> <NetID>@dc20.utdallas.edu:<destinationFolderLocation>
    ```

2. Login to each of dc20-dc23@utdallas.edu machines, using:
    ``` shell
    ssh <NetID>@dc##.utdallas.edu
    ```

3. On the dc machines, change current directory to P2 (on each machine), using:

    `cd P2/`

4. Compile the files on any machine by using (on any single machine):

    `javac ConnectingProcess.java Message.java VectorClock.java Process.java Start.java`

5. Run the following command on all the four machines as concurrent as possible
    `java Start <processId>`
    processId = 0, 1, 2, 3

    Eg: `java Start 0 on dc20`, `java Start 1 on dc21`, `java Start 2 on dc22,` `java Start 3 on dc23`

6. Terminate each process when completed.

7. Cleanup (Optional):
    ``` shell
    rm -r *.class
    ```
