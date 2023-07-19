/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * @author xiaozhou.tu
 * @date 2023/6/10
 */
public class LocalLogUtil {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

    public static void log(String format, Object... args) {
        if (args == null || args.length <= 0) {
            log(String.format(format));
        } else {
            log(String.format(format, Arrays.stream(args).map(LocalLogUtil::toJsonString).toArray()));
        }
    }

    public static void log(String message) {
        String className = Thread.currentThread().getStackTrace()[2].getClassName();
        String dateTime = FORMATTER.format(LocalDateTime.now());
        System.err.println("[" + dateTime + "] LOCAL [" + className + "] " + message);
    }

    private static String toJsonString(Object object) {
        return GSON.toJson(object);
    }

}
