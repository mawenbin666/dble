package io.mycat.plan.visitor;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLOrderBy;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectGroupByClause;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.expr.MySqlOrderingExpr;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.parser.MySqlStatementParser;
import com.alibaba.druid.sql.parser.SQLStatementParser;

import io.mycat.plan.common.item.Item;

public class TestMySQLItemVisitor {

	private String currentDb="test_schema";
	private int utf8Charset = 33;
	@Test
	public void testGroupby() {
		MySqlSelectQueryBlock query = getQuery("select col1,col2 from table1 group by col1,col2");
		SQLSelectGroupByClause groupBy = query.getGroupBy();
		int i = 0;
		for (SQLExpr p : groupBy.getItems()) {
			i++;
			String groupCol = "col" + i;
			MySQLItemVisitor v = new MySQLItemVisitor(this.currentDb, utf8Charset);
			p.accept(v);
			Item item = v.getItem();
			Assert.assertEquals(true, groupCol.equals(item.getItemName()));
		}
	}
	@Test
	public void testGroupbyOrder() {
		MySqlSelectQueryBlock query = getQuery("select col1,col2 from table1 group by col1 desc,col2 asc ");
		SQLSelectGroupByClause groupBy  = query.getGroupBy();
		int i = 0;
		for (SQLExpr p : groupBy.getItems()) {
			i++;
			String groupCol = "col" + i;
			MySqlOrderingExpr groupitem = (MySqlOrderingExpr) p;
			SQLExpr q = groupitem.getExpr();
			MySQLItemVisitor v = new MySQLItemVisitor(this.currentDb, utf8Charset);
			q.accept(v);
			Item item = v.getItem();
			Assert.assertEquals(true, groupCol.equals(item.getItemName()));
		}
	}
	@Test
	public void testGroupbyHaving() {
		MySqlSelectQueryBlock query = getQuery("select col1  from table1 group by col1 having count(*)>1  ");
		SQLSelectGroupByClause groupBy  = query.getGroupBy();
		SQLExpr q = groupBy.getHaving();
		MySQLItemVisitor v = new MySQLItemVisitor(this.currentDb, utf8Charset);
		q.accept(v);
		Item item = v.getItem();
		Assert.assertEquals(true, "COUNT(*) > 1".equals(item.getItemName()));
	}
	
	@Test
	public void testOrderby() {
		MySqlSelectQueryBlock query = getQuery("select col1,col2  from table1 order by col1 asc, col2 desc ");
		SQLOrderBy orderBy = query.getOrderBy();
		int i = 0; 
		for (SQLSelectOrderByItem p : orderBy.getItems()) {
			i++;
			String orderCol = "col" + i; 
			SQLExpr expr = p.getExpr();
			MySQLItemVisitor v = new MySQLItemVisitor(this.currentDb, utf8Charset);
			expr.accept(v);
			Item item = v.getItem();
			Assert.assertEquals(true, orderCol.equals(item.getItemName()));
		}
	}

	//TODO:ORDER BY /GROUP BY position 
	@Test
	public void testWhere() {
		MySqlSelectQueryBlock query = getQuery("select col1,col2  from table1 where a =1 "); 
		SQLExpr expr = query.getWhere();

		MySQLItemVisitor v = new MySQLItemVisitor(this.currentDb, utf8Charset);
		expr.accept(v);
		Item item = v.getItem();
		Assert.assertEquals(true, "a = 1".equals(item.getItemName()));
	}
	
	@Test
	public void testJoinCondition() {
		MySqlSelectQueryBlock query = getQuery("select a.col1,b.col2  from table1 a inner join table2 b on a.id =b.id"); 
		SQLJoinTableSource from = (SQLJoinTableSource)query.getFrom();

		MySQLItemVisitor v = new MySQLItemVisitor(this.currentDb, utf8Charset);
		from.getCondition().accept(v);
		Item item = v.getItem();
		Assert.assertEquals(true, "a.id = b.id".equals(item.getItemName()));
	}
	@Test
	public void testSelectItem() {
		MySqlSelectQueryBlock query = getQuery("select sum(col1) from table1 where a >1 "); 
		List<SQLSelectItem> items = query.getSelectList();
		
		MySQLItemVisitor v = new MySQLItemVisitor(this.currentDb, utf8Charset);
		items.get(0).accept(v);
		Item item = v.getItem();
		Assert.assertEquals(true, "SUM(col1)".equals(item.getItemName()));
	}

	//TODO:SELECTITEM(function) 
	private MySqlSelectQueryBlock getQuery(String sql){
		SQLSelect select = getSelect(sql);
		return (MySqlSelectQueryBlock)select.getQuery();
		
	}
//	private MySqlUnionQuery getUnionQuery(String sql){
//		SQLSelect select = getSelect(sql);
//		return (MySqlUnionQuery)select.getQuery();
//	}
	private SQLSelect getSelect(String sql){
		SQLSelectStatement stament = getSelectStatement(sql);
		return stament.getSelect();
	}
	private SQLSelectStatement getSelectStatement(String sql){
		SQLStatementParser parser = new MySqlStatementParser(sql);
		return (SQLSelectStatement)parser.parseStatement();
	}
}
