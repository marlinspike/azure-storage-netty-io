/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package storage.netty;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FileRegion;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
//import io.netty.util.internal.SocketUtils;
import io.netty.handler.stream.ChunkedFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.lang.String;

import javax.activation.MimetypesFileTypeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class is meant to be run against {@link HttpUploadServer}.
 */
public final class HttpUploadClient {

    private String base_url, account_name, account_key, FILE;
    private Mac hmacSha256;
    private byte[] key;

    static final String AB = "abcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();
    
    public HttpUploadClient(String account_name, String account_key){
    	
        this.base_url = "https://" + account_name + ".blob.core.windows.net";
        this.account_name = account_name;
        this.account_key= account_key;
    	
    }

    public void putBlob(String filepath) throws Exception {
    	this.FILE = filepath;
    	String resourceUrl = "/mycontainer/" + randomString(5);
        String putBlobUrl = base_url + resourceUrl;

        URI uriSimple = new URI(putBlobUrl);
        String scheme = uriSimple.getScheme() == null? "http" : uriSimple.getScheme();
        String host = uriSimple.getHost() == null? "127.0.0.1" : uriSimple.getHost();
        int port = uriSimple.getPort();
        if (port == -1) {
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
        }

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            System.err.println("Only HTTP(S) is supported.");
            return;
        }

        final boolean ssl = "https".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        if (ssl) {
            sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            sslCtx = null;
        }

        File file = new File(FILE);
        if (!file.canRead()) {
            throw new FileNotFoundException(FILE);
        }

        // Configure the client.
        EventLoopGroup group = new NioEventLoopGroup();

        // setup the factory: here using a mixed memory/disk based on size threshold
        HttpDataFactory factory = new DefaultHttpDataFactory(true); // Disk if MINSIZE exceed

        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file on exit (in normal exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = true; // should delete file on exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory

        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class).handler(new HttpUploadClientInitializer(sslCtx)); 

            // Simple Post form: factory used for big attributes
            formpost(b, host, port, uriSimple, resourceUrl, file, factory);

        } finally {
            // Shut down executor threads to exit.
            group.shutdownGracefully();

            // Really clean all temporary files if they still exist
            factory.cleanAllHttpDatas();
        }
        
    }

    /**
     * Standard post without multipart but already support on Factory (memory management)
     *
     * @return the list of HttpData object (attribute and file) to be reused on next post
     */
    private void formpost(
            Bootstrap bootstrap,
            String host, int port, URI uriSimple, String resourceUrl, File file, HttpDataFactory factory) throws Exception {
        // Start the connection attempt.
        Channel channel = bootstrap.connect(host, port).sync().channel();
        // Wait until the connection attempt succeeds or fails.
        //Channel channel = future.sync().channel();

        // Prepare the HTTP request.        
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, uriSimple.toASCIIString());
        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().add("x-ms-version", "2016-05-31");
        
        final DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dateTime = formatter.format(new Date());
        
        request.headers().set("x-ms-date", dateTime);
        request.headers().set("x-ms-blob-type", "BlockBlob");
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        long fileLength = raf.length();
        HttpUtil.setContentLength(request, fileLength);
        setContentTypeHeader(request, file);

        // Use the PostBody encoder
        request.headers().set("Authorization", "SharedKey " + account_name + ":" + AuthorizationHeader(account_name, account_key, "PUT", dateTime, request, resourceUrl, "", ""));

	    channel.write(request);

        ChannelFuture sendFileFuture = channel.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), channel.newProgressivePromise());
        	      
        // Write the end marker.
        ChannelFuture lastContentFuture = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        
        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
        	@Override
        	public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
        		if (total < 0) { // total unknown
        			System.err.println(future.channel() + " Transfer progress: " + progress);
        	    } else {
        	    	System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
        	    }
        	}

        	@Override
        	public void operationComplete(ChannelProgressiveFuture future) {
        		System.err.println(future.channel() + " Transfer complete.");
        	}
        });

        
    }

    public String AuthorizationHeader(String storageAccountName, String storageAccountKey, String method, String now, HttpRequest request, String resourceUrl, String ifMatch, String md5) throws InvalidKeyException
    {
        String MessageSignature;

        // This is the raw representation of the message signature.
        MessageSignature = String.format("%s\n\n\n%s\n%s\n%s\n\n\n%s\n\n\n\n%s%s",
            method,
            request.headers().getAsString("Content-Length"),
            md5,
            request.headers().getAsString("Content-Type"),
            ifMatch,
            GetCanonicalizedHeaders(request),
            GetCanonicalizedResource(resourceUrl, storageAccountName));
        System.out.println(MessageSignature);
        // Create the HMACSHA256 version of the storage key.
        String AuthorizationHeader = computeHmac256(storageAccountKey, MessageSignature);
        System.out.println(AuthorizationHeader);
        
        return AuthorizationHeader;
    }

    public String GetCanonicalizedHeaders(HttpRequest request)  {
    	
    	List<String> headerNameList = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();

        // Retrieve any headers starting with , 
        //   put them in a list and sort them by value.
        for (Entry<String, String> entry : request.headers()) 
        {        	
        	if (entry.getKey().toLowerCase().startsWith("x-ms-"))
            {
                headerNameList.add(entry.getKey().toLowerCase() + ":" + entry.getValue() + "\n");
            }
        }
        
        Collections.sort(headerNameList, (a, b) -> a.compareToIgnoreCase(b));
        
        for (String headerstring : headerNameList) 
        {        	
        	sb.append(headerstring);
        }  
        
        return sb.toString();

   }
    
    public String GetCanonicalizedResource(String resourceUrl, String storageAccountName)
    {
        StringBuilder builder = new StringBuilder("/");
        builder.append(storageAccountName);
        builder.append(resourceUrl);
        
        return builder.toString();

    }
    
    public synchronized String computeHmac256(String storage_key, final String value) throws InvalidKeyException {
            byte[] utf8Bytes = null;
            try {
                utf8Bytes = value.getBytes("UTF-8");

            
            if (hmacSha256 == null) {
                // Initializes the HMAC-SHA256 Mac and SecretKey.
                try {
                    hmacSha256 = Mac.getInstance("HmacSHA256");
                }
                catch (final NoSuchAlgorithmException e) {
                    throw new IllegalArgumentException();
                }
                hmacSha256.init(new SecretKeySpec(storage.netty.Base64.decode(storage_key), "HmacSHA256"));
            }
            
            return storage.netty.Base64.encode(hmacSha256.doFinal(utf8Bytes));
            
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
            
            return null;
    }
    
    String randomString( int len ){
    	StringBuilder sb = new StringBuilder( len );
    	for( int i = 0; i < len; i++ ) 
    		sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
    	return sb.toString();
    }
    
    private static void setContentTypeHeader(HttpRequest request, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
}
   
}



