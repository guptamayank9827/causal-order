## Causally Ordered Broadcast

This project implements Causally Ordered Broadcast of messages among 4 processes running on different machines on a network.
The 4 machines are completely connected to each other.

Causal order is ensured by using vector clocks (storing timestamps of each process in the network).

100 broadcast messages are sent by each process to all it's connecting processes.

Each local event (delivery or broadcast of messages) increments local time by 1.
Delivery of a message increments local time by taking a component-wise maximum of each element of the vector timestamp.

To run the project:

1. Mount the folder on any of the dc20-dc23 machines, using:
    ``` shell
    scp -r <sourceFolderLocation> <NetID>@dc20.utdallas.edu:<destinationFolderLocation>
    ```

2. Login to each of dc20-dc23@utdallas.edu machines, using:
    ``` shell
    ssh <NetID>@dc##.utdallas.edu
    ```

3. On the dc machines, change current directory to proj (on each machine), using:

    `cd proj/`

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
