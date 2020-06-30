# -*- coding: utf-8 -*-
"""
Created on Tue May 19 23:56:21 2020

@author: ADMIN
"""

import http.server
import argparse
from torrentool.api import Bencode
from http.server import BaseHTTPRequestHandler, HTTPServer
import logging
from urllib.parse import *
import json

def convert_url_hash_to_hex(infohash):
    """
    

    Parameters
    ----------
    infohash : string
        infohash as received in the http request

    Returns
    -------
    res : string
        hex representation of the infohash

    """
    idx=0
    res=''
    while idx < len(infohash):
        if infohash[idx]!='%':
            res+=infohash[idx].encode('utf8').hex()
            idx +=1
        else:
            res+=infohash[idx+1:idx+3]
            idx+=3
    return res

def get_info_hash_field(url_path):
    tokens=[t.split('=') for t in url_path.split('&')]
    for k in tokens:
        if k[0] == 'info_hash':
            return k[1]
    return 'ERROR'

class HTTPTorrentHandler(BaseHTTPRequestHandler):
    
    def __init__(self,request, client_address, server):
        super().__init__(request, client_address, server)
        
        
    def _set_response(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()
    
    def _get_hash(self,parsed_query):
        temp=parsed_query['info_hash'][0].decode('unicode-escape').encode('ISO-8859-1')
        
        return temp.hex()
    def do_GET(self):
        """
        Handling the GET requests for scrape and announce according to
        predefined data initialised in the server
        """
        json_responses= self.server.json_responses
        curr_indices = self.server.curr_indices
        self.parsed_path=urlparse(self.path)
        #logging.info(f'Parsed path:{self.parsed_path}')
        #parsed_query=parse_qs(self.parsed_path.query.replace('%',''),errors='backslashreplace'
        #                          )
        input_hash=get_info_hash_field(self.parsed_path.query) #self._get_hash(parsed_query)
        input_hash=convert_url_hash_to_hex(input_hash)
        #logging.info("GET request,\nPath: %s\nHeaders:\n%s\n", str(self.path), str(self.headers))
        logging.info(f'Accepted hash : {input_hash}')
        input_hash=input_hash.lower()
        if input_hash not in json_responses.keys():
            logging.info('Hash not found')
            self._set_response()
            self.wfile.write(Bencode.encode({'failure reason' : f'SERVER: Hash doesnt exist : {input_hash}'}))
            return
        #self.wfile.write(f"GET request for {self.path}\nExtra Data={self.server.data}".encode('utf-8'))
        #logging.info("GET request,\nPath: %s\nHeaders:\n%s\n", str(self.path), str(self.headers))
        cmd=self.parsed_path.path[1:]
        
        responses=json_responses[input_hash][cmd]
        response_idx = curr_indices[input_hash][cmd]
        logging.info(f'Cmd : {cmd}. Curr idx : {response_idx}')
        curr_indices[input_hash][cmd] = (curr_indices[input_hash][cmd]+1) % len(responses)
        res=responses[response_idx]
        
        self._set_response()
        self.wfile.write(res.encode('ISO-8859-1'))

    def do_POST(self):
        content_length = int(self.headers['Content-Length']) # <--- Gets the size of data
        post_data = self.rfile.read(content_length) # <--- Gets the data itself
        logging.info("POST request,\nPath: %s\nHeaders:\n%s\n\nBody:\n%s\n",
                str(self.path), str(self.headers), post_data.decode('utf-8'))

        self._set_response()
        self.wfile.write("POST request for {}".format(self.path).encode('utf-8'))

class HTTPTorrentServer(HTTPServer):
    def __init__(self,saddr, handler_class, extra_data):
        super().__init__(saddr, handler_class)
        self.json_responses=extra_data
        self.curr_indices={x:{'announce':0, 'scrape':0} for x in self.json_responses.keys()}
    

def run(server_class=HTTPTorrentServer, handler_class=HTTPTorrentHandler, port=8080,jsond='data.json'):
    logging.basicConfig(level=logging.INFO)
    server_address = ('', port)
    with open(jsond,'r') as inp:
        data=json.load(inp)
    httpd = server_class(server_address, handler_class, data)
    logging.info('Starting httpd...\n')
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    logging.info('Stopping httpd...\n#############')

def range_type(astr, minv=1, maxv=2**16-1):
    value = int(astr)
    if minv<= value <= maxv:
        return value
    else:
        raise argparse.ArgumentTypeError('value not in range %s-%s'%(minv,maxv))
        
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Torrent server for SD HW2 tests')
    parser.add_argument('json_file', type=str, help='json response file')
    parser.add_argument('port', type=range_type, help='port number for the server')
    args=parser.parse_args()

    run(port=args.port, jsond=args.json_file)
