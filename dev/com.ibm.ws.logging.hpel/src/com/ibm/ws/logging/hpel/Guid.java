package com.ibm.ws.logging.hpel;

//package org.eclipse.hyades.logging.core;

/**********************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * $Id: Guid.java,v 1.9 2006/07/24 20:01:06 paules Exp $
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 * 
 * Note: This class originates from Eclipse and only contains modifications to the package name.
 **********************************************************************/

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Universally Unique IDentifier (UUID) or Globally Unique IDentifier (GUID) implementation for
 * generating a hexadecimal string representation of a GUID.
 * <p>
 * GUIDs generated with this class are expected to be unique across all space and time.
 * <p>
 * GUIDs generated with this class are 128 bits in length. As such, the hexadecimal string
 * representation of the 128 bit GUID is 32 characters long (e.g. see {@link #toString()}.
 * <p>
 * The hexadecimal string representation of GUIDs generated with this class are
 * compliant with XML Schema <i>ID</i> primitive data type
 * (see <a href="http://www.w3.org/TR/2000/CR-xmlschema-2-20001024/#ID>http://www.w3.org/TR/2000/CR-xmlschema-2-20001024/#ID</a>).
 * As such, the most significant hexadecimal character is guaranteed to be a non-numeric character.
 * <p>
 * GUIDs generated with this class are composed of the following properties:
 * <p>
 * <ul>
 * <li>Unique time stamp</li>
 * <li>Clock sequence to avoid decreasing time stamps</li>
 * <li>Unique node identifier (e.g. pseudo IEEE 802 MAC address)</li>
 * </ul>
 * <p>
 * For example:
 * <p>
 * <pre>
 * |&lt;------------------------- 32 bits --------------------------&gt;| Octet Note
 * 
 * +--------------------------------------------------------------+
 * | low 32 bits of time | 0-3 Low field of the time stamp
 * +-------------------------------+-------------------------------
 * | mid 16 bits of time | 4-5 Middle field of the time stamp
 * +-------+-----------------------+
 * | vers. | hi 12 bits of time | 6-7 High field of the time stamp multiplexed with the version number
 * +-------+-------+---------------+
 * |Res| clkSeqHi | 8 High field of the clock sequence multiplexed with the variant
 * +---------------+
 * | clkSeqLow | 9 Low field of the clock sequence.
 * +---------------+----------...-----+
 * | node ID | 8-16 Unique node identifier
 * +--------------------------...-----+
 * </pre>
 * <p>
 * GUIDs are generated using the following programming model:
 * <p>
 * <code>
 * String guid = Guid.generate();
 * <code>
 * <p>
 * <b>Note</b>: This class has several required preconditions to generate GUIDs unique across
 * all space and time:
 * <p>
 * <ol>
 * <li>Since the a millisecond time stamp is the most granular unit of time in Java and multiple time
 * stamps may be generated within the one millisecond, cross-thread and cross-process (e.g. JVM) synchronization
 * is required. Threading locking is used to ensure the atomic generation of unique time stamps between threads.
 * File locking (only truly atomic operation in Java) us used to ensure the atomic generation of unique time stamps
 * between processes (e.g. JVM) on the same host. As such, a locking file is created and delete in the default temporary
 * file path (e.g. &lt;default temp file path&gt;/guid.lock). Insufficient permissions on the local file system to create the
 * locking file in the default temporary file path and/or insufficient <code>java.io.FilePermission</code>s when Java Security
 * is enabled to create the locking file in the default temporary file path will increase the probability of duplicate GUIDs.
 * </li>
 * <li>
 * Since the IEEE 802 Media Access Control (MAC) address of the local Network Interface Card (NIC) cannot
 * be retrieved in Java, a pseudo IEEE 802 MAC address is required. The pseudo IEEE 802 MAC address is generated by
 * applying a hash function to a concatenation of the following random data:
 * <ul>
 * <li>Cross-thread and cross-process unique time stamp (milliseconds)</li>
 * <li>Hexadecimal representation of the hash code of a new <code>java.lang.Object</code></li>
 * <li>Free memory in the JVM (bytes)</li>
 * <li>Total memory in the JVM (bytes)</li>
 * </ul>
 * The MD5 hash algorithm is used to generate pseudo IEEE 802 MAC address. This hash algorithm is invoked through the
 * <code>java.security.MessageDigest</code> which requires a provider for the MD5 hash algorithm
 * (see Java Cryptography Architecture (JCA)). If the provider for the MD5 hash algorithm is not available in the run-time
 * environment, a simple additive hash algorithm is used over the random data. However, it is computationally feasible for
 * this simple additive hash algorithm to generate the same output for two different inputs thereby increasing the probability
 * of duplicate GUIDs.
 * </li>
 * </ol>
 * <p>
 * This GUID implementation is based on the
 * <a href="http://ftp.ics.uci.edu/pub/ietf/webdav/uuid-guid/draft-leach-uuids-guids-01.txt">IETF Specification for UUIDs and GUIDs</a>:
 * <p>
 * <i>
 * Copyright (C) The Internet Society 1997. All Rights Reserved.
 * <p>
 * This document and translations of it may be copied and furnished to
 * others, and derivative works that comment on or otherwise explain it
 * or assist in its implementation may be prepared, copied, published
 * and distributed, in whole or in part, without restriction of any
 * kind, provided that the above copyright notice and this paragraph are
 * included on all such copies and derivative works. However, this
 * document itself may not be modified in any way, such as by removing
 * the copyright notice or references to the Internet Society or other
 * Internet organizations, except as needed for the purpose of
 * </i>
 * <p>
 * 
 * @author Paul E. Slauenwhite
 * @version July 24, 2006
 * @since January 26, 2005
 */
public final class Guid {

    /**
     * The low order 32 bits of the adjusted time stamp.
     */
    private int timeLow = 0;

    /**
     * The middle order 16 bits of the adjusted time stamp.
     */
    private short timeMiddle = 0;

    /**
     * The high order 12 bits of the adjusted time stamp multiplexed with the
     * version number. The version number is in the most significant 4 bits
     * and is <code>1010</code> or <code>0xA</code>.
     */
    private short timeHighWithVersion = 0;

    /**
     * The high order 14 bits of the clock sequence multiplexed with two
     * reserved bits to avoid decreasing time stamps. The reserved bits are
     * in the most significant 4 bits and are <code>1100</code> or <code>0xC</code>.
     */
    private byte clockSequenceHighWithReserved = 0;

    /**
     * The low order 16 bits of the clock sequence.
     */
    private byte clockSequenceLow = 0;

    private static long lastTimeStamp = 0;

    private static long clockSequence = 0;

    private static int clockSequenceAdjustment = 0;

    /**
     * The unique node identifier consists of the
     * IEEE 802 Media Access Control (MAC) address of the local Network
     * Interface Card (NIC). Since the IEEE 802 MAC address cannot be retrieved
     * in Java, a pseudo IEEE 802 MAC address is required.
     */
    private static byte[] pseudoIEEE802MACAddress = null;

    private static Random randomNumberGenerator = null;

    private static boolean isInitialized = false;

    /**
     * Hexadecimal characters.
     */
    private final static char[] HEXADECIMAL_CHARACTERS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    /**
     * Current thread lock for synchronization.
     */
    private final static Object LOCK = new Object();

    private final static String TEMPORARY_DIRECTORY = getSystemProperty("java.io.tmpdir");

    /**
     * No-argument constructor to create a 128 bit/32 character GUID.
     * <p>
     * GUIDs generated with this class are composed of the following properties:
     * <p>
     * <ul>
     * <li>Unique time stamp</li>
     * <li>Clock sequence to avoid decreasing time stamps</li>
     * <li>Unique node identifier (e.g. pseudo IEEE 802 MAC address)</li>
     * </ul>
     * <p>
     * For example:
     * <p>
     * <pre>
     * |&lt;------------------------- 32 bits --------------------------&gt;| Octet Note
     * 
     * +--------------------------------------------------------------+
     * | low 32 bits of time | 0-3 Low field of the time stamp
     * +-------------------------------+-------------------------------
     * | mid 16 bits of time | 4-5 Middle field of the time stamp
     * +-------+-----------------------+
     * | vers. | hi 12 bits of time | 6-7 High field of the time stamp multiplexed with the version number
     * +-------+-------+---------------+
     * |Res| clkSeqHi | 8 High field of the clock sequence multiplexed with the variant
     * +---------------+
     * | clkSeqLow | 9 Low field of the clock sequence.
     * +---------------+----------...-----+
     * | node ID | 8-16 Unique node identifier
     * +--------------------------...-----+
     * </pre>
     * <p>
     */
    public Guid() {

        long adjustedTimestamp = 0;

        // Instead of having small synchronized blocks, synchronize the 
        // initialization phase (e.g. prevent multiple access to the clockSequenceAdjustment 
        // and lastTimeStamp values).
        synchronized (LOCK) {

            // The first time this method is called, it will need to obtain the
            // operating system internals. It uses the JNI functions defined
            // earlier, and will set a static variable to avoid this in
            // subsequent calls.
            if (!isInitialized) {

                long uniqueTimeStamp = getUniqueTimeStamp();

                pseudoIEEE802MACAddress = getPseudoIEEE802MACAddress(uniqueTimeStamp);

                //Instantiate and seed the random number generator:
                try {

                    randomNumberGenerator = SecureRandom.getInstance("MD5");
                    randomNumberGenerator.setSeed(uniqueTimeStamp);
                } catch (Throwable t) {

                    //If a provider for the MD5 hash algorithm (see Java Cryptography Architecture (JCA) APIs) 
                    //is not available in the run-time environment, use the java.util.Random class as the
                    //random number generator:  
                    randomNumberGenerator = new Random(uniqueTimeStamp);
                }

                clockSequence = randomNumberGenerator.nextLong();

                isInitialized = true;
            }

            // Get the adjusted time stamp and use to generate a random clock
            // sequence.
            // We must handle the situation where the current time is the same
            // as the previous generation or has actually gone backwards.
            boolean timeIsValid = true;

            do {
                adjustedTimestamp = getAdjustedTimestamp();

                // Clock has been reset, generate a new random clockSequence. The two
                // most significant bits will be set to the reserved value
                // later.
                if (adjustedTimestamp < lastTimeStamp) {
                    clockSequence = randomNumberGenerator.nextLong();
                    clockSequenceAdjustment = 0;
                }

                // Normal situation, reset the adjustment and leave the do-while
                // loop.
                if (adjustedTimestamp > lastTimeStamp) {
                    clockSequenceAdjustment = 0;
                }

                // Clock hasn't changed resolution, adjust the clock sequence
                // for uniqueness.
                // If we hit the maximum adjustment, we must "spin" until the
                // clock is incremented.
                if (adjustedTimestamp == lastTimeStamp) {

                    //Only adjust the clock sequence by at most 9999:
                    if (clockSequenceAdjustment < 9999) {
                        clockSequenceAdjustment++;
                    }
                    else {
                        timeIsValid = false;
                    }
                }
            } while (!timeIsValid);

            lastTimeStamp = adjustedTimestamp;

            if (clockSequenceAdjustment != 0) {
                adjustedTimestamp += clockSequenceAdjustment;
            }

            // Construct a Version 1 GUID from the component pieces and
            // constants. The variable tempValue allows us to perform masking
            // and bit shifting operations separate from casting the value into
            // the appropriate size.
            timeLow = ((int) (adjustedTimestamp & 0xFFFFFFFF));

            timeMiddle = ((short) ((adjustedTimestamp >>> 32) & 0xFFFF));

            //GUID version 1 = 0xA000;
            timeHighWithVersion = ((short) (((short) ((adjustedTimestamp >>> 48) & 0x0FFF)) | ((short) (0xA000))));

            clockSequenceLow = ((byte) (clockSequence & 0xFF));

            //GUID reserved = 0xC0:
            clockSequenceHighWithReserved = ((byte) (((byte) ((clockSequence & 0x3F00) >>> 8)) | ((byte) (0xC0))));
        }
    }

    /**
     * Returns the hexadecimal string representation of the 128 bit
     * <code>Guid</code> object.
     * <p>
     * The <code>toString</code> method will take the binary data in each of
     * the internal components of the <code>Guid</code> object, covert each
     * byte to its hexadecimal character equivalent, and return the string.
     * <p>
     * 
     * @return The hexadecimal string representation of the 128 bit
     *         <code>Guid</code>, which is 32 characters long.
     */
    @Override
    public String toString() {

        final char[] stringBuffer = new char[32];

        //Current position in the local string buffer:
        int pos = 0;

        //Number of bits to shift the value being converted to hexadecimal:
        int shift = 12;

        while (pos < 4) {

            stringBuffer[pos] = HEXADECIMAL_CHARACTERS[(timeHighWithVersion >>> shift & 0xF)];

            shift -= 4;

            pos++;
        }

        shift = 12;

        while (pos < 8) {

            stringBuffer[pos] = HEXADECIMAL_CHARACTERS[(timeMiddle >>> shift & 0xF)];

            shift -= 4;

            pos++;
        }

        shift = 28;

        while (pos < 16) {

            stringBuffer[pos] = HEXADECIMAL_CHARACTERS[(timeLow >>> shift & 0xF)];

            shift -= 4;

            pos++;
        }

        stringBuffer[pos++] = HEXADECIMAL_CHARACTERS[(clockSequenceHighWithReserved >>> 4 & 0xF)];
        stringBuffer[pos++] = HEXADECIMAL_CHARACTERS[(clockSequenceHighWithReserved & 0xF)];
        stringBuffer[pos++] = HEXADECIMAL_CHARACTERS[(clockSequenceLow >>> 4 & 0xF)];
        stringBuffer[pos++] = HEXADECIMAL_CHARACTERS[(clockSequenceLow & 0xF)];

        int i = 0;

        while (pos < 32) {

            stringBuffer[pos] = HEXADECIMAL_CHARACTERS[(pseudoIEEE802MACAddress[i] >>> 4 & 0xF)];

            stringBuffer[pos + 1] = HEXADECIMAL_CHARACTERS[(pseudoIEEE802MACAddress[i] & 0xF)];

            i++;

            pos += 2;
        }

        return (new String(stringBuffer).trim());
    }

    /**
     * Returns the 6 byte pseudo IEEE 802 Media Access Control (MAC) address.
     * <p>
     * Since the IEEE 802 Media Access Control (MAC) address of the local Network
     * Interface Card (NIC) cannot be retrieved in Java, a pseudo IEEE 802 MAC address
     * is required. The pseudo IEEE 802 MAC address is generated by applying a hash
     * function to a concatenation of the following random data:
     * <ul>
     * <li>Cross-thread and cross-process unique time stamp (milliseconds)</li>
     * <li>Hexadecimal representation of the hash code of a new <code>java.lang.Object</code></li>
     * <li>Free memory in the JVM (bytes)</li>
     * <li>Total memory in the JVM (bytes)</li>
     * </ul>
     * The MD5 hash algorithm is used to generate pseudo IEEE 802 MAC address. This hash
     * algorithm is invoked through the <code>java.security.MessageDigest</code> which requires
     * a provider for the MD5 hash algorithm (see Java Cryptography Architecture (JCA)). If the
     * provider for the MD5 hash algorithm is not available in the run-time environment, a simple
     * additive hash algorithm is used over the random data. However, it is computationally feasible
     * for this simple additive hash algorithm to generate the same output for two different inputs.
     * <p>
     * 
     * @param uniqueTimeStamp Cross-thread and cross-process unique time stamp (milliseconds)
     * @return 6 byte pseudo IEEE 802 MAC address.
     */
    private synchronized byte[] getPseudoIEEE802MACAddress(long uniqueTimeStamp) {

        byte[] ieee802Addr = new byte[6];
        byte[] currentTime = String.valueOf(uniqueTimeStamp).getBytes();
        byte[] localHostAddress = null;

        try {
            localHostAddress = InetAddress.getLocalHost().getAddress();
        } catch (UnknownHostException u) {
            localHostAddress = new byte[] { 127, 0, 0, 1 };
        }

        //Use the unsigned hexadecimal representation of the hash code of the object instead
        //of the java.lang.Object#toString() to eliminate the 'java.lang.Object@' prefix
        //(see java.lang.Object#toString()) which never changes:
        byte[] inMemoryObject = Integer.toHexString(new Object().hashCode()).getBytes();
        byte[] freeMemory = String.valueOf(Runtime.getRuntime().freeMemory()).getBytes();
        byte[] totalMemory = String.valueOf(Runtime.getRuntime().totalMemory()).getBytes();
        byte[] messageDigestInput = new byte[currentTime.length + localHostAddress.length + inMemoryObject.length + freeMemory.length + totalMemory.length];
        int messageDigestInputIndex = 0;

        System.arraycopy(currentTime, 0, messageDigestInput, messageDigestInputIndex, currentTime.length);
        messageDigestInputIndex += currentTime.length;

        System.arraycopy(localHostAddress, 0, messageDigestInput, messageDigestInputIndex, localHostAddress.length);
        messageDigestInputIndex += localHostAddress.length;

        System.arraycopy(inMemoryObject, 0, messageDigestInput, messageDigestInputIndex, inMemoryObject.length);
        messageDigestInputIndex += inMemoryObject.length;

        System.arraycopy(freeMemory, 0, messageDigestInput, messageDigestInputIndex, freeMemory.length);
        messageDigestInputIndex += freeMemory.length;

        System.arraycopy(totalMemory, 0, messageDigestInput, messageDigestInputIndex, totalMemory.length);

        try {

            //Compute the message digest:
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            md5.reset();

            System.arraycopy(md5.digest(messageDigestInput), 0, ieee802Addr, 0, 6);
        } catch (Throwable t) {

            //If a provider for the MD5 hash algorithm (see Java Cryptography Architecture (JCA) APIs) 
            //is not available in the run-time environment, a simple additive hash algorithm is used over the random 
            //data.  The simple additive hash algorithm is similar to that used in java.lang.String#hashCode(). 
            //That is:
            //	b[0]*31^(n-1) + b[1]*31^(n-2) + ... + b[n-1]
            //where:
            //	b[i] = i-th byte
            //	n = total number of bytes
            //	^ = exponentiation
            //Note:  It is computationally feasible for this simple additive hash algorithm to generate the same output 
            //for two different inputs.

            int hashCode = 0;

            for (int counter = 0; counter < messageDigestInput.length; counter++) {
                hashCode = (31 * hashCode) + messageDigestInput[counter];
            }

            byte[] hashCodeBytes = Integer.toHexString(hashCode).getBytes();

            //Zero pad the hash code if less than 6 bytes:
            if (hashCodeBytes.length < 6) {
                System.arraycopy(hashCodeBytes, 0, ieee802Addr, (6 - hashCodeBytes.length), hashCodeBytes.length);
            }

            //Use the last 6 bytes of the hash code which contains the most varying data and :
            else {
                System.arraycopy(hashCodeBytes, (hashCodeBytes.length - 6), ieee802Addr, 0, 6);
            }
        }

        //Turn on the high order bit of the first byte:
        ieee802Addr[0] |= 0x80;

        return ieee802Addr;
    }

    /**
     * Returns an adjusted current time stamp.
     * <p>
     * The system time is recorded as the number of milliseconds since
     * January 1, 1970. The coordinated universal time is measured from
     * the beginning of the Gregorian Calendar, so we must adjust back
     * with the number of milliseconds between October 15, 1582 and
     * January 1, 1970. Then we convert into 100 nanosecond resolution by
     * multiplying by 10**4 (0x01B21DD213814000L == 122192928000000000
     * 100 nanosecond resolution).
     * <p>
     * 
     * @return Adjusted current time stamp.
     */
    private synchronized long getAdjustedTimestamp() {
        return ((System.currentTimeMillis() * 10000L) + 0x01B21DD213814000L);
    }

    /**
     * Returns an unique current time stamp.
     * <p>
     * Since the a millisecond time stamp is the most granular unit of time
     * in Java and multiple time stamps may be generated within the one millisecond,
     * cross-thread and cross-process (e.g. JVM) synchronization is required.
     * <p>
     * Threading locking is used to ensure the atomic generation of unique time stamps
     * between threads. File locking (only truly atomic operation in Java) us used to
     * ensure the atomic generation of unique time stamps between processes (e.g. JVM)
     * on the same host.
     * <p>
     * 
     * @return Unique current time stamp.
     */
    private synchronized long getUniqueTimeStamp() {

        //Create a default time stamp:
        long timeStamp = System.currentTimeMillis();

        //Lock file for ensuring we create a unique time stamp for all
        //processes on the system:
        File lockFile = null;

        try {

            lockFile = new File(TEMPORARY_DIRECTORY, "guid.lock");

            //Last modified time stamp for the lock file:
            long lastModified = fileLastModified(lockFile);

            //Maximum absolute time stamp to execute the lock file algorithm:
            //NOTE: This upper bound (1000 milliseconds or 1 second) provides an escape mechanism for the
            //scenario where a previous instance of the JVM crashed and a stray lock file persists on the 
            //local file system.
            long maxWaitTimeStamp = (System.currentTimeMillis() + 1000);

            //Loop until we can create the lock file (e.g. exclusive rights)
            //since creating a file is atomic or the maximum absolute time 
            //stamp to execute the lock file algorithm has expired:
            while (true) {

                try {

                    //After the lock file has been created, we have exclusive
                    //rights to the lock file and enter the process 
                    //synchronized block:
                    if (lockFile.createNewFile()) {
                        break;
                    }

                    //If we cannot create the lock file, only continue until
                    //the maximum absolute time stamp to execute the lock 
                    //file algorithm has expired:
                    else if (System.currentTimeMillis() > maxWaitTimeStamp) {

                        //If the lock file has not been updated before the
                        //maximum absolute time stamp to execute the lock 
                        //file algorithm has expired, delete the lock file 
                        //and attempt to execute the lock algorithm again:
                        if (fileLastModified(lockFile) <= lastModified) {

                            delete(lockFile);

                            //Only permit this instance of the algorithm to
                            //attempt to delete the lock file once:
                            lastModified = -1;
                        }

                        //If a lock cannot be obtained before the maximum
                        //absolute time stamp to execute the lock file 
                        //algorithm has expired, return the non-atomic 
                        //current time stamp:
                        else {
                            return timeStamp;
                        }
                    }
                } catch (IOException i) {

                    //Return the non-atomic current time stamp since the 
                    //lock file could not be created.
                    return timeStamp;
                }
            }

            //Wait 1/10 second (100 milliseconds) to ensure a unique time
            //stamp:
            try {
                Thread.sleep(100);
            } catch (InterruptedException i) {
                //Ignore since we are only sleeping.
            }

            //Create a unique time stamp as an atomic operation:
            timeStamp = System.currentTimeMillis();
        } catch (SecurityException s) {
            //Ignore all security exceptions and exit gracefully.
        } finally {

            try {

                //After the lock file has been atomically deleted, we release 
                //the exclusive rights to the lock file exit the process
                //synchronized block:
                if (lockFile != null) {
                    delete(lockFile);
                }
            } catch (SecurityException s) {
                //Ignore all security exceptions and exit gracefully.
            }
        }

        return timeStamp;
    }

    /**
     * @param lockFile
     */
    private boolean delete(final File lockFile) {
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                return lockFile.delete();
            }
        });
    }

    /**
     * @param lockFile
     * @return
     */
    private long fileLastModified(final File lockFile) {
        return AccessController.doPrivileged(new PrivilegedAction<Long>() {
            @Override
            public Long run() {
                return lockFile.lastModified();
            }
        });
    }

    /**
     * Static convenience API for generating a new GUID.
     * <p>
     * This API is equivalent to calling <code>new Guid().toString()</code>.
     * <p>
     * The API returns the hexadecimal string representation of a new
     * <code>Guid</code> object.
     * <p>
     * This API will create a new <code>Guid</code> object, take the binary
     * data in each of the internal components of the <code>Guid</code>
     * object, covert each byte to its hexadecimal character equivalent and
     * return the string.
     * <p>
     * 
     * @return The hexadecimal string representation of a new <code>Guid</code>
     *         object, which is 32 characters long.
     */
    public static String generate() {
        return (new Guid().toString());
    }

    /**
     * @param string
     * @return
     */
    private static String getSystemProperty(final String prop) {
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getProperty(prop);
            }

        });
    }

}