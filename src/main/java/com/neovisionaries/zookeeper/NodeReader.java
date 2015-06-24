/*
 * Copyright (C) 2015 Neo Visionaries Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neovisionaries.zookeeper;


import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;


/**
 * Znode reader. If the target znode to read does not exist when
 * {@link #start()} method is called, this implementation waits
 * for the target znode to be created and then reads its content.
 *
 * <pre style="border: 1px solid black; padding: 0.5em; margin: 1em;">
 * <span style="color: darkgreen;">// Prepare a ZooKeeper instance.</span>
 * ZooKeeper zooKeeper = ...
 *
 * <span style="color: darkgreen;">// The path of the target znode to read.</span>
 * String path = ...
 *
 * <span style="color: darkgreen;">// Prepare a Listener implementation.</span>
 * NodeReader.{@link Listener} listener = <span style="color: purple; font-weight: bold;">new</span> NodeReader.Listener() {
 *     <span style="color: gray;">&#x40;Override</span>
 *     <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onRead(NodeReader, byte[], Stat)
 *     onRead}(NodeReader reader, byte[] data, Stat stat) {
 *         System.out.println(<span style="color: mediumblue;">"Successfully read the znode."</span>);
 *     }
 *
 *     <span style="color: gray;">&#x40;Override</span>
 *     <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onGaveUp(NodeReader)
 *     onGaveUp}(NodeReader reader) {
 *         System.out.println(<span style="color: mediumblue;">"Gave up reading the znode."</span>);
 *     }
 * };
 *
 * <span style="color: darkgreen;">// Read the target znode.</span>
 * <span style="color: purple; font-weight: bold;">new</span> NodeReader()
 *     .{@link #setZooKeeper(ZooKeeper) setZooKeeper}(zooKeeper)
 *     .{@link #setPath(String) setPath}(path)
 *     .{@link #setListener(Listener) setListener}(listener)
 *     .{@link #start()};
 * </pre>
 *
 * @since 1.2
 */
public class NodeReader
{
    /**
     * Listener interface to receive the result of reading.
     */
    public interface Listener
    {
        /**
         * Called when the content of the target znode was read
         * successfully.
         *
         * @param reader
         *         The {@link NodeReader} instance which this
         *         listener is associated with.
         *
         * @param data
         *         The content of the target znode.
         *
         * @param stat
         *         Statistics of the target znode.
         */
        void onRead(NodeReader reader, byte[] data, Stat stat);


        /**
         * Called when the {@link NodeReader} gave up reading
         * the target znode.
         *
         * <p>
         * This callback is called when it detects one of the following
         * when it tried to call a {@link ZooKeeper}'s method
         * ({@link ZooKeeper#getData(String, boolean, DataCallback, Object) getData()}
         * or {@link ZooKeeper#exists(String, Watcher, StatCallback, Object) exists()}.
         * Note that this method may not be called so soon and may not
         * be called at all.
         * </p>
         *
         * <blockquote>
         * <ol>
         * <li>The given {@link ZooKeeper} instance reports {@link
         * ZooKeeper.States#AUTH_FAILED AUTH_FAILED} or {@link
         * ZooKeeper.States#CLOSED CLOSED}.
         * <li>This instance is marked as 'shouldFinish' by {@link #finish()}.
         * </ol>
         * </blockquote>
         *
         * @param reader
         *         The {@link NodeReader} instance which this
         *         listener is associated with.
         */
        void onGaveUp(NodeReader reader);
    }


    /**
     * An empty implementation of {@link Listener} interface.
     */
    public static class Adapter implements Listener
    {
        @Override
        public void onRead(NodeReader reader, byte[] data, Stat stat)
        {
        }


        @Override
        public void onGaveUp(NodeReader reader)
        {
        }
    }


    private ZooKeeper mZooKeeper;
    private String mPath;
    private Listener mListener;
    private boolean mShouldFinish;
    private DataCallback mReadCallback = new ReadCallback();
    private Watcher mTrackWatcher = new TrackWatcher();
    private StatCallback mTrackCallback = new TrackCallback();


    public NodeReader()
    {
    }


    public NodeReader(ZooKeeper zooKeeper)
    {
        mZooKeeper = zooKeeper;
    }


    /**
     * Get the {@link ZooKeeper} instance to read the target znode.
     *
     * @return
     *         The {@link ZooKeeper} instance.
     */
    public ZooKeeper getZooKeeper()
    {
        return mZooKeeper;
    }


    /**
     * Set the {@link ZooKeeper} instance to read the target znode.
     *
     * <p>
     * If no {@link ZooKeeper} instance is set when {@link #start()} is called,
     * an {@code IllegalStateException} is thrown.
     * </p>
     *
     * @param zooKeeper
     *         A {@link ZooKeeper} instance.
     *
     * @return
     *         {@code this} object.
     */
    public NodeReader setZooKeeper(ZooKeeper zooKeeper)
    {
        mZooKeeper = zooKeeper;

        return this;
    }


    /**
     * Get the path of the target znode to read.
     *
     * @return
     *         The path of the target znode to read.
     */
    public String getPath()
    {
        return mPath;
    }


    /**
     * Set the path of the target znode to read.
     *
     * <p>
     * If no path is set when {@link #start()} is called, an {@code
     * IllegalStateException} is thrown.
     * </p>
     *
     * @param path
     *         The path of the target znode.
     *
     * @return
     *         {@code this} object.
     */
    public NodeReader setPath(String path)
    {
        mPath = path;

        return this;
    }


    /**
     * Get the listener.
     *
     * @return
     *         The listener.
     */
    public Listener getListener()
    {
        return mListener;
    }


    /**
     * Set a listener.
     *
     * @param listener
     *         A listener.
     *
     * @return
     *         {@code this} object.
     */
    public NodeReader setListener(Listener listener)
    {
        mListener = listener;

        return this;
    }


    /**
     * Start reading the target znode.
     *
     * @return
     *         {@code this} object.
     *
     * @throws
     * @throws IllegalStateException
     *         <ul>
     *           <li>No {@link ZooKeeper} instance is set.
     *           <li>No path is set.
     *         </ul>
     */
    public NodeReader start()
    {
        // If a ZooKeeper instance is not set.
        if (mZooKeeper == null)
        {
            // A ZooKeeper instance must be set before start().
            throw new IllegalStateException("A ZooKeeper instance must be set.");
        }

        // If a path is not set.
        if (mPath == null)
        {
            // A path must be set before start().
            throw new IllegalStateException("A path must be set.");
        }

        // Schedule reading.
        read();

        return this;
    }


    /**
     * Mark as 'shouldFinish' not to schedule ZooKeeper callbacks
     * any further.
     *
     * <p>
     * Note that calling this method does not remove an existing
     * {@link Watcher} which is watching the target znode.
     * </p>
     *
     * @return
     *         {@code this} object.
     */
    public NodeReader finish()
    {
        synchronized (this)
        {
            mShouldFinish = true;
        }

        return this;
    }


    private boolean shouldFinish()
    {
        synchronized (this)
        {
            if (mShouldFinish)
            {
                return true;
            }
        }

        switch (mZooKeeper.getState())
        {
            case AUTH_FAILED:
            case CLOSED:
                return true;

            default:
                return false;
        }
    }


    private boolean finishIfAppropriate()
    {
        boolean shouldFinish = shouldFinish();

        if (shouldFinish)
        {
            callOnGaveUp();
        }

        return shouldFinish;
    }


    private void callOnRead(byte[] data, Stat stat)
    {
        if (mListener == null)
        {
            return;
        }

        try
        {
            mListener.onRead(this, data, stat);
        }
        catch (RuntimeException e)
        {
            // Ignore.
        }
    }


    private void callOnGaveUp()
    {
        if (mListener == null)
        {
            return;
        }

        try
        {
            mListener.onGaveUp(this);
        }
        catch (RuntimeException e)
        {
            // Ignore.
        }
    }


    private void read()
    {
        if (finishIfAppropriate())
        {
            // Terminate the callback chain here.
            return;
        }

        mZooKeeper.getData(mPath, false, mReadCallback, null);
    }


    private void track()
    {
        if (finishIfAppropriate())
        {
            // Terminate the callback chain here.
            return;
        }

        mZooKeeper.exists(mPath, mTrackWatcher, mTrackCallback, null);
    }


    private class ReadCallback implements DataCallback
    {
        @Override
        public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat)
        {
            switch (Code.get(rc))
            {
                case OK:
                    // Successfully obtained the data of the znode.
                    callOnRead(data, stat);
                    return;

                case NONODE:
                    // The znode does not exist. Wait for it to be created.
                    track();
                    return;

                default:
                    // Retry to read the znode.
                    read();
                    return;
            }
        }
    }


    private class TrackWatcher implements Watcher
    {
        @Override
        public void process(WatchedEvent event)
        {
            if (event.getType() == EventType.NodeCreated)
            {
                // Read the node.
                read();
            }
        }
    }


    private class TrackCallback implements StatCallback
    {
        @Override
        public void processResult(int rc, String path, Object ctx, Stat stat)
        {
            switch (Code.get(rc))
            {
                case OK:
                    // The znode exists. Read the node.
                    read();
                    return;

                case NONODE:
                    // Wait for the watcher to be triggered.
                    return;

                default:
                    // Keep tracking the znode.
                    track();
                    return;
            }
        }
    }
}
