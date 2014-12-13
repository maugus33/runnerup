/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.tracker.component;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Pair;

import java.util.HashMap;

/**
 * Created by jonas on 12/11/14.
 *
 * Class for managing a set of TrackerComponents as if they were one
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class TrackerComponentCollection implements TrackerComponent {

    final Handler handler = new Handler();
    final HashMap<String, Pair<TrackerComponent, ResultCode>> components =
            new HashMap<String, Pair<TrackerComponent, ResultCode>>();
    final HashMap<String, TrackerComponent> pending =
            new HashMap<String, TrackerComponent>();

    public TrackerComponent addComponent(TrackerComponent component) {
        components.put(component.getName(),
                new Pair<TrackerComponent, ResultCode>(component, ResultCode.RESULT_OK));
        return component;
    }

    public TrackerComponent getComponent(String key) {
        synchronized (components) {
            if (components.containsKey(key))
                return components.get(key).first;
            else if (pending.containsKey(key))
                return pending.get(key);
            return null;
        }
    }

    public ResultCode getResultCode(String key) {
        synchronized (components) {
            if (components.containsKey(key))
                return components.get(key).second;
            else if (pending.containsKey(key))
                return ResultCode.RESULT_PENDING;
            return ResultCode.RESULT_ERROR;
        }
    }

    @Override
    public String getName() {
        return "TrackerComponentCollection";
    }

    /**
     * Called by Tracker during initialization
     */
    @Override
    public ResultCode onInit(final Callback callback, Context context) {
        return forEach("onInit", new Func1() {
            @Override
            public ResultCode apply(TrackerComponent comp0, Callback callback0, Context context0) {
                return comp0.onInit(callback0, context0);
            }
        }, callback, context);
    }

    private ResultCode getResult(HashMap<String, Pair<TrackerComponent, ResultCode>> components) {
        ResultCode res = ResultCode.RESULT_OK;
        for (Pair<TrackerComponent,ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_ERROR_FATAL)
                res = ResultCode.RESULT_ERROR_FATAL;
            else if (pair.second == ResultCode.RESULT_ERROR)
                res = ResultCode.RESULT_ERROR;
        }
        return res;
    }

    /**
     * Called by Tracker when workout starts
     */
    @Override
    public void onStart() {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onStart();
            }
        }
    }

    /**
     * Called by Tracker when workout is paused
     */
    @Override
    public void onPause() {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onPause();
            }
        }
    }

    /**
     * Called by Tracker when workout is resumed
     */
    @Override
    public void onResume() {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onResume();
            }
        }
    }

    /**
     * Called by Tracker when workout is complete
     */
    @Override
    public void onComplete(boolean discarded) {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onComplete(discarded);
            }
        }
    }

    /**
     * Called by tracked after workout has ended
     */
    @Override
    public ResultCode onEnd(final Callback callback, Context context) {
        return forEach("onEnd", new Func1() {
            @Override
            public ResultCode apply(TrackerComponent comp0, Callback callback0, Context context0) {
                return comp0.onEnd(callback0, context0);
            }
        }, callback, context);
    }

    private interface Func1 {
        ResultCode apply(TrackerComponent component, Callback callback, Context context);
    }

    private ResultCode forEach(String msg, final Func1 func, final Callback callback,
                               Context context) {
        synchronized (components) {
            HashMap<String, Pair<TrackerComponent, ResultCode>> list =
                    new HashMap<String, Pair<TrackerComponent, ResultCode>>();
            list.putAll(components);
            components.clear();

            for (final String key : list.keySet()) {
                final TrackerComponent component = list.get(key).first;
                ResultCode res = func.apply(component, new Callback() {
                    @Override
                    public void run(final TrackerComponent component, final ResultCode resultCode) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (components) {
                                    TrackerComponent check = pending.remove(key);
                                    assert (check == component);
                                    components.put(key, new Pair<TrackerComponent, ResultCode>(
                                            component, resultCode));
                                    if (pending.isEmpty()) {
                                        callback.run(TrackerComponentCollection.this,
                                                getResult(components));
                                    }
                                }
                            }
                        });
                    }
                }, context);
                if (res != ResultCode.RESULT_PENDING) {
                    components.put(key, new Pair<TrackerComponent, ResultCode>(component, res));
                } else {
                    pending.put(key, component);
                }
            }
        }
        if (!pending.isEmpty())
            return ResultCode.RESULT_PENDING;
        else
            return getResult(components);
    }
}