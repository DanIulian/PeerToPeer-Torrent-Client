# PeerToPeer-Torrent-Client

The purpose of this project is to design and implement a scalable and fully functional PeerToPeer Torrent Application for files transfer.

## Table of Contents

1. [Project overview](#project-overview)
2. [Requirements](#requirements)
3. [Architecture](#architecture)
    - [Communication Protocol](#communication-protocol)
    - [Main Node](#main-node)
    - [Client](#client)



## Project Overview

 The purpose of this project is to implement in Java a scalable and fully functional PeerToPeer Torrent Application for files transfer. The application consists of a Central
Node which stores all the information regarding the shared files, and a variable number of Clients (peers) which connect to the Central Node, search for information about a file, and download it from the peers that have the requested file.
 The clients connect and leave the network in an asynchronous manner and can choose to publish a file or to download one. Information about the published files will be available
on the Main Node. However, the actual file will reside only at the client that published it, or at the clients that have downloaded it. Each file is uniquely identified by 
its name and when it is first published, it is splitted in fragments of equal sizes. The port and the IP for each Client will be variable and will depend on the time that the
Client connected to the network. However, the Main Node will have a fixed public IP and port, known in advance by each client that connects to it.
 Every operation completed by the clients and the Main Node will be logged using the log4j API.


## Requirements

* A machine with a 2 GB Ram

* Ubuntu 14.04 or higher

* JAVA Version 7 or higher and log4j JAR

* Apache ANT for deployment


## Architecture

##### Communication Protocol

The peers and Main Node communicates with each other using a custom protocol described by the comm_proto package which contains the classes FileDescription, FragmentDescription
and FragmentFile. Each message exchanged between two entities in the network uses a 12 bytes HEADER. The first 4 bytes contains the thpe of the message ( Publish a fragment,
Request Information about a File, Publish a File). The next 4 bytes specifies the length of the data message, and the final 4 bytes contains the port on which the client listens. The DATA filed of the message contains the serial representation of one of the three classes mentioned earlier or the serial representation of the name of the file that
a client requests information about. Every time an entity in the network receive a message, it will first read 12 bytes which represents the HEADER and then it will read the DATA.


#### Main Node

The Main Node is implemented using the central_nod_pkg package which has three classes. The MainServer class starts the Main Node, the CentralNode class 
contains the implementation of the MainNode, and the PublishedFile class is used to encapsulate all the information required about a published file.
The server has a list of all published file. Each element of the list has information about the file name, the file length, the first fragment length. 
There is also a list of FragmentFile objects that has information about each fragment and all the clients that have the fragment in question. 
The Main Node implementation uses asynchronous sockets and a selector. The server runs in a different thread. 


#### Client

The client uses a thread pool for uploading and downloading files. Each thread is managing a single fragment of the file. There is also a special thread that
exchanges messages with the Main Node, the class ClientAsServer implements the behaviour of this thread. For each fragment downloaded, a new socket connection
is established with the other peer; the connection is closed after the fragment is successfully received. For the thread pool I used Executor class and I have set the maximum
number of connections to 10. The class ClientRequest is in charge of processing a request for a fragment, and the class AskForFragment is in charge of downloading a fragment
from another client. The fragment size depends on the length of the file and can vary from 2KB to 512KB
Each client will store the files in different directory.


## Copyright and License

This application is provided under the [MIT-license](https://github.com/DanIulian/PeerToPeer-Torrent-Client/blob/master/LICENSE).



