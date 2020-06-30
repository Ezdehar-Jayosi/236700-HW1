# CourseApp: Assignment 0

## Authors
* Ezdehar Jayosi, 313380164
* Mahmoud Yazbak, 315737809

## Notes

### Implementation Summary

App Layer
implements all coursetorrent app logic.

Storage Layer
Peer,Statistics,Torrent
 - Statistics, contains informaion about trackers of the torrent identified by [infohash].
 - Wrappers for read/write functions in SecureStorage
 - Our own test implementation of SecureStorage
 - Peers for each torrent are contained in a single list
 - Statistics are stored as Key-Value pairs of Announce/Scrape URL and ScrapeData for each  torrent
 - Each torrents data, specifically the announce list, is mapped to it's infohash
 
Utils Layer
Bencoding which is responsible for decoding/encoding.
HTTPGet, a wrapper for the HTTP Get function from Fuel by kittinunf, an HTTP library for kotlin.

### Testing Summary
We bound our implementation of SecureStorage, and our MockK of HTTPGet, to CourseTorrent, using Guice.
We mocked return values of the http get requests so we could get consistent values from our GET requests

### Difficulties
Guice was a bit tricky to understand, and it was a bit difficult to get examples of everything within the 
announce/scrape response, since we needed to find torrents that gave us the desired response.

### Feedback
-