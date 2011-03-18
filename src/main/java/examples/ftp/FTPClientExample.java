/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package examples.ftp;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;

/**
 * This is an example program demonstrating how to use the FTPClient class.
 * This program connects to an FTP server and retrieves the specified
 * file.  If the -s flag is used, it stores the local file at the FTP server.
 * Just so you can see what's happening, all reply strings are printed.
 * If the -b flag is used, a binary transfer is assumed (default is ASCII).
 * See below for further options.
 */
public final class FTPClientExample
{

    public static final String USAGE =
        "Usage: ftp [options] <hostname> <username> <password> [<remote file> [<local file>]]\n" +
        "\nDefault behavior is to download a file and use ASCII transfer mode.\n" +
        "\t-a - use local active mode (default is local passive)\n" +
        "\t-b - use binary transfer mode\n" +
        "\t-c cmd - issue arbitrary command (remote is used as a parameter if provided) \n" +
        "\t-d - list directory details using MLSD (remote is used as the pathname if provided)\n" +
        "\t-e - use EPSV with IPv4 (default false)\n" +
        "\t-f - issue FEAT command (remote and local files are ignored)\n" +
        "\t-h - list hidden files (applies to -l and -n only)\n" +
        "\t-k secs - use keep-alive timer (setControlKeepAliveTimeout)\n" +
        "\t-l - list files using LIST (remote is used as the pathname if provided)\n" +
        "\t-n - list file names using NLST (remote is used as the pathname if provided)\n" +
        "\t-p protocol - use FTPSClient with the specified protocol \n" +
        "\t-s - store file on server (upload)\n" +
        "\t-t - list file details using MLST (remote is used as the pathname if provided)\n" +
        "\t-w msec - wait time for keep-alive reply (setControlKeepAliveReplyTimeout)\n" +
        "\t-# - add hash display during transfers\n";

    public static final void main(String[] args)
    {
        boolean storeFile = false, binaryTransfer = false, error = false, listFiles = false, listNames = false, hidden = false;
        boolean localActive = false, useEpsvWithIPv4 = false, feat = false, printHash = false;
        boolean mlst = false, mlsd = false;
        long keepAliveTimeout = -1;
        int controlKeepAliveReplyTimeout = -1;
        int minParams = 5; // listings require 3 params
        String protocol = null; // SSL protocol
        String doCommand = null;

        int base = 0;
        for (base = 0; base < args.length; base++)
        {
            if (args[base].equals("-s")) {
                storeFile = true;
            }
            else if (args[base].equals("-a")) {
                localActive = true;
            }
            else if (args[base].equals("-b")) {
                binaryTransfer = true;
            }
            else if (args[base].equals("-c")) {
                doCommand = args[++base];
                minParams = 3;
            }
            else if (args[base].equals("-d")) {
                mlsd = true;
                minParams = 3;
            }
            else if (args[base].equals("-e")) {
                useEpsvWithIPv4 = true;
            }
            else if (args[base].equals("-f")) {
                feat = true;
                minParams = 3;
            }
            else if (args[base].equals("-h")) {
                hidden = true;
            }
            else if (args[base].equals("-k")) {
                keepAliveTimeout = Long.parseLong(args[++base]);
            }
            else if (args[base].equals("-l")) {
                listFiles = true;
                minParams = 3;
            }
            else if (args[base].equals("-n")) {
                listNames = true;
                minParams = 3;
            }
            else if (args[base].equals("-p")) {
                protocol = args[++base];
            }
            else if (args[base].equals("-t")) {
                mlst = true;
                minParams = 3;
            }
            else if (args[base].equals("-w")) {
                controlKeepAliveReplyTimeout = Integer.parseInt(args[++base]);
            }
            else if (args[base].equals("-#")) {
                printHash = true;
            }
            else {
                break;
            }
        }

        int remain = args.length - base;
        if (remain < minParams) // server, user, pass, remote, local [protocol]
        {
            System.err.println(USAGE);
            System.exit(1);
        }

        String server = args[base++];
        int port = 0;
        String parts[] = server.split(":");
        if (parts.length == 2){
            server=parts[0];
            port=Integer.parseInt(parts[1]);
        }
        String username = args[base++];
        String password = args[base++];

        String remote = null;
        if (args.length - base > 0) {
            remote = args[base++];
        }

        String local = null;
        if (args.length - base > 0) {
            local = args[base++];
        }

        final FTPClient ftp;
        if (protocol == null ) {
            ftp = new FTPClient();            
        } else {
            ftp = new FTPSClient(protocol);
        }

        if (printHash) {
            ftp.setCopyStreamListener(createListener());
        }
        if (keepAliveTimeout >= 0) {
            ftp.setControlKeepAliveTimeout(keepAliveTimeout);
        }
        if (controlKeepAliveReplyTimeout >= 0) {
            ftp.setControlKeepAliveReplyTimeout(controlKeepAliveReplyTimeout);
        }
        ftp.setListHiddenFiles(hidden);

        // suppress login details
        ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out), true));

        try
        {
            int reply;
            if (port > 0) {
                ftp.connect(server, port);                
            } else {
                ftp.connect(server);
            }
            System.out.println("Connected to " + server + " on "+ftp.getRemotePort());

            // After connection attempt, you should check the reply code to verify
            // success.
            reply = ftp.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply))
            {
                ftp.disconnect();
                System.err.println("FTP server refused connection.");
                System.exit(1);
            }
        }
        catch (IOException e)
        {
            if (ftp.isConnected())
            {
                try
                {
                    ftp.disconnect();
                }
                catch (IOException f)
                {
                    // do nothing
                }
            }
            System.err.println("Could not connect to server.");
            e.printStackTrace();
            System.exit(1);
        }

__main:
        try
        {
            if (!ftp.login(username, password))
            {
                ftp.logout();
                error = true;
                break __main;
            }

            System.out.println("Remote system is " + ftp.getSystemType());

            if (binaryTransfer)
                ftp.setFileType(FTP.BINARY_FILE_TYPE);

            // Use passive mode as default because most of us are
            // behind firewalls these days.
            if (localActive) {
                ftp.enterLocalActiveMode();
            } else {
                ftp.enterLocalPassiveMode();
            }

            ftp.setUseEPSVwithIPv4(useEpsvWithIPv4);

            if (storeFile)
            {
                InputStream input;

                input = new FileInputStream(local);

                ftp.storeFile(remote, input);

                input.close();
            }
            else if (listFiles)
            {
                for (FTPFile f : ftp.listFiles(remote)) {
                    System.out.println(f);
                }
            }
            else if (mlsd)
            {
                for (FTPFile f : ftp.mlistDir(remote)) {
                    System.out.println(f.getRawListing());
                    System.out.println(f.toFormattedString());
                }
            }
            else if (mlst)
            {
                FTPFile f = ftp.mlistFile(remote);
                if (f != null){
                    System.out.println(f.toFormattedString());
                }
            }
            else if (listNames)
            {
                for (String s : ftp.listNames(remote)) {
                    System.out.println(s);
                }

            }
            else if (feat)
            {
                if (ftp.features()) {
//                    Command listener has already printed the output
//                    for(String s : ftp.getReplyStrings()) {
//                        System.out.println(s);
//                    }
                    if (remote != null) { // See if the command is present
                      for(String s : ftp.getReplyStrings()) {
                          if (s.indexOf(remote) == 1) { // After first space
                              System.out.println("FEAT supports: "+s);
                          }
                      }
                    }

                } else {
                    System.out.println("Failed: "+ftp.getReplyString());
                } 
            }
            else if (doCommand != null)
            {
                if (ftp.doCommand(doCommand, remote)) {
//                  Command listener has already printed the output
//                    for(String s : ftp.getReplyStrings()) {
//                        System.out.println(s);
//                    }
                } else {
                    System.out.println("Failed: "+ftp.getReplyString());
                }
            }
            else
            {
                OutputStream output;

                output = new FileOutputStream(local);

                ftp.retrieveFile(remote, output);

                output.close();
            }

            ftp.noop(); // check that control connection is working OK

            ftp.logout();
        }
        catch (FTPConnectionClosedException e)
        {
            error = true;
            System.err.println("Server closed connection.");
            e.printStackTrace();
        }
        catch (IOException e)
        {
            error = true;
            e.printStackTrace();
        }
        finally
        {
            if (ftp.isConnected())
            {
                try
                {
                    ftp.disconnect();
                }
                catch (IOException f)
                {
                    // do nothing
                }
            }
        }

        System.exit(error ? 1 : 0);
    } // end main

    private static CopyStreamListener createListener(){
        return new CopyStreamListener(){
            private long megsTotal = 0;
            public void bytesTransferred(CopyStreamEvent event) {
                bytesTransferred(event.getTotalBytesTransferred(), event.getBytesTransferred(), event.getStreamSize());
            }

            public void bytesTransferred(long totalBytesTransferred,
                    int bytesTransferred, long streamSize) {
                long megs = totalBytesTransferred / 1000000;
                for (long l = megsTotal; l < megs; l++) {
                    System.err.print("#");
                }
                megsTotal = megs;
            }
        };
    }
}

