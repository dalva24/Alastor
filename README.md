
# ![Alastor-logo](Alastor.png?raw=true)  Alastor - Massively Concurrent File Transfer System

A file transfer server and client that transfers file supporting hundreds of simultaneous connection. Made in Java utilizing gRPC.

A quick weekend project of mine, so don't expect it to be very optimized.

## Overview

Alastor is a file transfer server and client that supports file transfers with hundreds of connections using gRPC. There is no limit to the number of connections, but it is only tested up to 200 connections.

Each file that are downloaded will be split into 50kB chunks by default, and are then individually transmitted via the multiple connections. The number of connections and chunk size can be configured to optimize for a specific network characteristic.

Each chunk that are received are then validated with its CRC32 checksum to ensure content integrity, and then reassembled on client's disk.

Simple security and client validation is provided by using a pre-shared API key that are to be provided on each client request, which require TLS for effective usage. TLS can be provided externally using NGINX (or other web server) reverse proxy and an HTTPS site configuration with proper certificates (Lets Encrypt, etc.)

:warning: Be warned, that using Alastor with hundreds or thousands of connections may possibly trigger your network, or your ISP's, firewall response. It will also slow down your system considerably, and eat up memory like no tomorrow. The author of Alastor is not responsible for any damages or lossess caused by the usage of this software.

## Motivation

The idea to this project started one day when I was trying to download huge files from a private server using a home connection. I noticed that the download speed was only in the low 30s kBps, where the server itself is easily capable of pushing 20-30MBps to other servers. Interestingly, when I do a speed test, or when I download from google drive using the same home connection, speeds of around 4MBps were observed.

I then hypothesize that my ISP must have did something funny with my connection, preferring google servers and speedtest servers to give the illusion of fast speed, while limiting bandwidth to lesser-known servers in order to reap a bigger profit.

RIP Net neutrality in my country, indeed, but I refuse to submit under the tyrrany of evil ISPs. I then proceed to look for ways to go around this problem.

> "***Necessity is the mother of invention***"  - *english proverb*

A spark of idea came when using a download manager capable of downloading files with up to 10 parts simultaneously that pretty much multiplies the total download bandwidth by 10x. But it was still not enough, as it only gave me speeds of 300kBps, which is still way under the 4MBps ideal speed.

"How hard would it be to make a simple downloader?", I thought. I've also been wanting to learn gRPC anyways, so, 2 bird with 1 stone, right?

Thus the Alastor was born.

To the ISPs:
> "***However, I do not approve of this selfish wish as it currently stands***" - *Alastor (3E21-00:54)*

## Building

Requirements:
- JDK 11
- Maven

Using maven to build .jar:
```
mvn clean   (optional)
mvn install
```

Builds will go to `target/` directory

Using IDE: configure your IDE tool to invoke `mvn install`

## Running

### Server

Requirements:
- JRE 11
- Network interface with publicly-accessible IP
- (Optional) NGINX Reverse Proxy setup
- (Optional) NGINX HTTPS setup

Using packaged jar:
```
java -jar <jar name>.jar serve
```
Usage help will then be shown. You can use tmux for a simple server damonization

### Client

Requirements:
- JRE 11

Using packaged jar:
```
java -jar <jar name>.jar get
```

Usage help will then be shown.

## Deploying as Daemon 

TODO

## Future plans
There are some additional ideas that came to mind, although priority to implement them is relatively low, such as:
- Download pause and resuming capability, by saving downloaded chunk information as a metadata file besides the downloaded file
- Data encryption and client authentication without the need of TLS / HTTPS Webserver
- 
