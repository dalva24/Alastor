
# ![Alastor-logo](Alastor.png?raw=true)  Alastor - Massively Concurrent File Transfer System

<p align="center">
  <img width="880" height="400" src="client-ss.png">
</p>

A file transfer server and client that supports hundreds of simultaneous connection, with extremely resilient connection management and retrying. Perfect for some of the world's worst internet connections. Made in Java utilizing gRPC.

This is just a quick weekend project of mine, so the code may not be as refined. Expect horrible memory usage and system resource inefficiencies all around.

## Overview

Alastor is a file transfer server and client that supports file transfers with hundreds of connections using gRPC. There is no limit to the number of connections, but it is only tested up to 200 connections.

Each file that are downloaded will be split into 50kB chunks by default, and are then individually transmitted via the multiple connections. The number of connections and chunk size can be configured to optimize for a specific network characteristic.

Each chunk that are received are then validated with its CRC32 checksum to ensure content integrity, and then reassembled on client's disk.

Simple security and client validation is provided by using a pre-shared API key that are to be provided on each client request, which require TLS for effective usage. TLS can be provided externally using NGINX (or other web server) reverse proxy and an HTTPS site configuration with proper certificates (Lets Encrypt, etc.)

:warning: Be warned, that using Alastor with hundreds or thousands of connections may possibly trigger your network, or your ISP's, firewall response. It will also slow down your system considerably, and eat up memory like no tomorrow. The author of Alastor is not responsible for any damages or lossess caused by the usage of this software.

## Features

- Download single files with as much connections as you want. The connection amount is not limited, but are only tested with up to 200 parallel connections.
- Extremely resilient connection tracking with automatic unlimited retries on every single chunk on errors or timeouts. Perfect for some of the worst internet connections in the world.
- Supports file sizes above 4GB (tested with a 5GB file), with a supposed theoretical limit of 2^64 Bytes (obviously untested).
- Supports connecting to TLS-enabled server, such as when ran behind NGINX TLS-enabled reverse proxy.
- Unencrypted connections also supported with `-n` flag, but as its nature, insecure.
- Downloaded chunks are also individually CRC32-checked to ensure integrity (with whole-file on-the-fly checksum checking planned)

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

## Performance

A test is performed by downloading a 3GB example file from a private server to local computer on my home connection which supposedly have 4MBps hard bandwidth cap.

Without Alastor, file served via standard HTTPS download via a web browser have an average of 30kB/s download speed.

Using Alastor via TLS by NGINX reverse proxy, with 200 parallel connection and 100kB chunk size, full bandwidth saturation is achieved with an average download speed of 4000kB/s. However, when the active connection drops at the end of the download session due to the threads not having any more chunks to process, the download speed also falls drastically. A possible solution to this problem is to download the same final few chunks in parallel utilizing the entire requested active connection amount (in this example 200), only saving the data from the fastest returning connection thread.

Memory usage on 200 parallel connection on 1 client: Client ~733MB, Server ~122MB

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

Nginx reverse proxy can be setup by referring to the following example:
```
http {
    server {
        listen 80 http2;

        location / {
            grpc_pass grpc://localhost:41457;
			access_log off;
        }
    }
}
```
Source: https://www.nginx.com/blog/nginx-1-13-10-grpc/
Note that by default, Alastor Server runs at port 41457
Note that `access_log off;` is important, as otherwise nginx logs will be bombarded by the tens of thousands of connections that will be made during a download

### Client

Requirements:
- JRE 11

Using packaged jar:
```
java -jar <jar name>.jar get
```

Usage help will then be shown.

## Deploying as Daemon 

TODO - file auto-close on server still needs to be implemented. currently it is bugged.

## Future plans / TODOs
TODOs:
- When connection servants no longer have available chunks to download, simultaneously download the same final chunks and only use the fastest responding ones.
  this is to improve the final download speeds, which usually slows down considerably.
- Set adjustable timeout - or Set dynamic timeout based on remaining active connections?
- Whole-file on-the-fly checksum testing that will not add additional drive reads

There are some additional ideas that came to mind, although priority to implement them is relatively low, such as:
- Download pause and resuming capability, by saving downloaded chunk information as a metadata file besides the downloaded file
- Data encryption and client authentication without the need of TLS / HTTPS Webserver
- Server file auto-close when inactive (ServerFileHandler.java:60)
