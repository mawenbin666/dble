/*
 * Copyright (C) 2016-2020 ActionTech.
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
 */

package com.actiontech.dble.manager.information;

import com.actiontech.dble.backend.mysql.CharsetUtil;
import com.actiontech.dble.backend.mysql.PacketUtil;
import com.actiontech.dble.backend.mysql.nio.handler.query.DMLResponseHandler;
import com.actiontech.dble.backend.mysql.nio.handler.util.HandlerTool;
import com.actiontech.dble.config.ErrorCode;
import com.actiontech.dble.config.Fields;
import com.actiontech.dble.manager.ManagerConnection;
import com.actiontech.dble.meta.ColumnMeta;
import com.actiontech.dble.net.mysql.FieldPacket;
import com.actiontech.dble.net.mysql.RowDataPacket;
import com.actiontech.dble.plan.common.field.Field;
import com.actiontech.dble.plan.common.item.Item;
import com.actiontech.dble.plan.common.item.ItemField;
import com.actiontech.dble.plan.visitor.MySQLItemVisitor;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.util.*;

public final class ManagerTableUtil {
    private ManagerTableUtil() {

    }

    public static String valueToString(SQLExpr valueExpr) throws SQLException {
        String value;
        if (valueExpr instanceof SQLIntegerExpr) {
            SQLIntegerExpr intExpr = (SQLIntegerExpr) valueExpr;
            value = intExpr.getNumber() + "";
        } else if (valueExpr instanceof SQLCharExpr) {
            SQLCharExpr charExpr = (SQLCharExpr) valueExpr;
            value = charExpr.getText();
        } else {
            throw new SQLNonTransientException("Not Supported of Value EXPR :" + valueExpr.toString());
        }
        return value;
    }


    public static List<RowDataPacket> getFoundRows(ManagerConnection c, ManagerWritableTable managerTable, SQLExpr whereExpr) {
        List<Item> realSelects = new ArrayList<>();
        for (ColumnMeta cm : managerTable.getColumnsMeta()) {
            ItemField col = new ItemField(null, null, cm.getName());
            realSelects.add(col);
        }
        List<FieldPacket> fieldPackets = makeField(realSelects, managerTable.getTableName());

        MySQLItemVisitor mev = new MySQLItemVisitor(c.getSchema(), c.getCharset().getResultsIndex(), null, null);
        whereExpr.accept(mev);
        List<Field> sourceFields = HandlerTool.createFields(fieldPackets);

        List<RowDataPacket> foundRows = new ArrayList<>();
        List<RowDataPacket> allRows = managerTable.getRow(realSelects, c.getCharset().getResults());
        for (RowDataPacket row : allRows) {
            HandlerTool.initFields(sourceFields, row.fieldValues);
            Item whereItem = HandlerTool.createItem(mev.getItem(), sourceFields, 0, false, DMLResponseHandler.HandlerType.WHERE);
            if (whereItem.valBool()) {
                foundRows.add(row);
            }
        }
        return foundRows;
    }

    private static List<FieldPacket> makeField(List<Item> realSelects, String tableName) {
        List<FieldPacket> totalResult = new ArrayList<>();
        for (Item select : realSelects) {
            int type = Fields.FIELD_TYPE_VAR_STRING;
            if (!select.basicConstItem()) {
                type = ManagerSchemaInfo.getInstance().getTables().get(tableName).getColumnType(select.getItemName());
            }
            FieldPacket field = PacketUtil.getField(select.getItemName(), type);
            totalResult.add(field);
        }
        return totalResult;
    }


    public static Set<LinkedHashMap<String, String>> getAffectPks(ManagerConnection c, ManagerWritableTable managerTable, List<RowDataPacket> foundRows) throws SQLException {
        Set<LinkedHashMap<String, String>> affectPks = new HashSet<>(foundRows.size());
        for (RowDataPacket row : foundRows) {
            LinkedHashMap<String, String> affectPk = new LinkedHashMap<>();
            int i = 0;
            for (String columnName : managerTable.getColumnNames()) {
                if (managerTable.getPrimaryKeyColumns().contains(columnName)) {
                    String charset = CharsetUtil.getJavaCharset(c.getCharset().getResultsIndex());
                    try {
                        String value = new String(row.getValue(i), charset);
                        affectPk.put(columnName, value);
                    } catch (UnsupportedEncodingException e) {
                        throw new SQLException("Unknown charset '" + c.getCharset().getResults() + "'", "HY000", ErrorCode.ER_UNKNOWN_CHARACTER_SET);
                    }
                }
                i++;
            }
            affectPks.add(affectPk);
        }
        return affectPks;
    }
}