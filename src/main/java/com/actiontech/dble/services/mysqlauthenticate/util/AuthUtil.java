/*
 * Copyright (C) 2016-2020 ActionTech.
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
 */

package com.actiontech.dble.services.mysqlauthenticate.util;

import com.actiontech.dble.DbleServer;
import com.actiontech.dble.config.ErrorCode;
import com.actiontech.dble.config.model.user.*;
import com.actiontech.dble.net.connection.AbstractConnection;
import com.actiontech.dble.net.connection.FrontendConnection;
import com.actiontech.dble.services.mysqlauthenticate.PluginName;
import com.actiontech.dble.services.mysqlauthenticate.SecurityUtil;
import com.actiontech.dble.singleton.FrontendUserManager;
import com.actiontech.dble.singleton.TraceManager;

import java.security.NoSuchAlgorithmException;

public final class AuthUtil {
    private AuthUtil() {
    }

    public static String auhth(UserName user, AbstractConnection connection, byte[] seed, byte[] password, String schema, PluginName plugin) {
        TraceManager.TraceObject traceObject = TraceManager.serviceTrace(connection.getService(), "user-auth-for-right&password");
        try {
            UserConfig userConfig = DbleServer.getInstance().getConfig().getUsers().get(user);
            if (userConfig == null) {
                return "Access denied for user '" + user + "' with host '" + connection.getHost() + "'";
            }
            if (userConfig instanceof RwSplitUserConfig) {
                return "this version does not support rwSplitUser";
            }

            FrontendConnection fcon = (FrontendConnection) connection;

            //normal user login into manager port
            if (fcon.isManager() && !(userConfig instanceof ManagerUserConfig)) {
                return "Access denied for user '" + user + "'";
            } else if (!fcon.isManager() && userConfig instanceof ManagerUserConfig) {
                //manager user login into server port
                return "Access denied for manager user '" + user + "'";
            }

            if (userConfig.getWhiteIPs().size() > 0 && !userConfig.getWhiteIPs().contains(connection.getHost())) {
                return "Access denied for user '" + user + "' with host '" + connection.getHost() + "'";
            }

            // check password
            if (!checkPassword(seed, password, userConfig.getPassword(), plugin)) {
                return "Access denied for user '" + user + "', because password is incorrect";
            }

            if (!DbleServer.getInstance().getConfig().isFullyConfigured() && !fcon.isManager()) {
                return "Access denied for user '" + user + "', because there are some empty dbGroup/fake dbInstance";
            }

            if (userConfig instanceof ShardingUserConfig) {
                // check sharding
                switch (checkSchema(schema, (ShardingUserConfig) userConfig)) {
                    case ErrorCode.ER_BAD_DB_ERROR:
                        return "Unknown database '" + schema + "'";
                    case ErrorCode.ER_DBACCESS_DENIED_ERROR:
                        return "Access denied for user '" + user + "' to database '" + schema + "'";
                    default:
                        break;
                }
            }

            //check maxconnection
            int userLimit = userConfig.getMaxCon();
            switch (FrontendUserManager.getInstance().maxConnectionCheck(user, userLimit, userConfig instanceof ManagerUserConfig)) {
                case SERVER_MAX:
                    return "Access denied for user '" + user + "',too many connections for dble server";
                case USER_MAX:
                    return "Access denied for user '" + user + "',too many connections for this user";
                default:
                    break;
            }

            return null;
        } finally {
            TraceManager.finishSpan(connection.getService(), traceObject);
        }
    }


    private static boolean checkPassword(byte[] seed, byte[] password, String pass, PluginName name) {

        // check null
        if (pass == null || pass.length() == 0) {
            return password == null || password.length == 0;
        }
        if (password == null || password.length == 0) {
            return false;
        }

        // encrypt
        byte[] encryptPass = null;
        try {
            switch (name) {
                case mysql_native_password:
                    encryptPass = SecurityUtil.scramble411(pass.getBytes(), seed);
                    break;
                case caching_sha2_password:
                    encryptPass = SecurityUtil.scramble256(pass.getBytes(), seed);
                    break;
                default:
                    throw new NoSuchAlgorithmException();
            }
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
        if (encryptPass != null && (encryptPass.length == password.length)) {
            int i = encryptPass.length;
            while (i-- != 0) {
                if (encryptPass[i] != password[i]) {
                    return false;
                }
            }
        } else {
            return false;
        }

        return true;
    }

    private static int checkSchema(String schema, ShardingUserConfig userConfig) {
        if (schema == null) {
            return 0;
        }
        if (DbleServer.getInstance().getSystemVariables().isLowerCaseTableNames()) {
            schema = schema.toLowerCase();
        }
        if (!DbleServer.getInstance().getConfig().getSchemas().containsKey(schema)) {
            return ErrorCode.ER_BAD_DB_ERROR;
        }

        if (userConfig.getSchemas().contains(schema)) {
            return 0;
        } else {
            return ErrorCode.ER_DBACCESS_DENIED_ERROR;
        }
    }

}
