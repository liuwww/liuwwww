package org.liuwww.db.service.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.liuwww.db.context.DbContext;
import org.liuwww.db.context.TableMetaData;
import org.liuwww.db.dao.IDataDao;
import org.liuwww.db.dao.impl.DataDao;
import org.liuwww.db.service.IDataTemplate;
import org.liuwww.db.sql.Column;
import org.liuwww.db.sql.Row;
import org.liuwww.db.sql.RowUtil;
import org.liuwww.db.sql.SqlBean;
import org.liuwww.db.sql.SqlBeanUtil;
import org.liuwww.db.sql.TableDefaultValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.alibaba.fastjson.JSON;
import org.liuwww.common.Idgen.IdGeneratorUtil;
import org.liuwww.common.entity.TableEntity;
import org.liuwww.common.execption.DbException;
import org.liuwww.common.util.EntryUtil;
import org.liuwww.common.util.StringUtil;

public class DataTemplate implements IDataTemplate
{
    private Logger logger = LoggerFactory.getLogger(DataTemplate.class);

    private IDataDao dataDao;

    public DataTemplate()
    {
        this.dataDao = new DataDao();
    }

    @Autowired
    public DataTemplate(IDataDao dataDao)
    {
        this.dataDao = dataDao;
    }

    @Override
    public Row insert(String tableName, Map<String, Object> fieldVals) throws DbException
    {
        return insert(tableName, fieldVals, null);
    }

    @Override
    public <T> T insert(TableEntity<T> entity) throws DbException
    {
        return insert(entity, null);
    }

    @Override
    public <T> List<T> insert(List<? extends TableEntity<T>> entityList) throws DbException
    {
        return insert(entityList, null);
    }

    @Override
    public List<Row> insert(String tableName, List<Map<String, Object>> fList) throws DbException
    {
        return insert(tableName, fList, null);
    }

    @Override
    public int update(String tableName, Map<String, Object> fieldVals) throws DbException
    {
        return update(tableName, fieldVals, null);
    }

    @Override
    public int delete(String tableName, Map<String, Object> idMap) throws DbException
    {
        return delete(tableName, idMap, null);
    }

    @Override
    public int updateRows(String tableName, Map<String, Object> valMap, Map<String, Object> paramMap) throws DbException
    {
        return updateRows(tableName, valMap, paramMap, null);
    }

    @Override
    public int deleteRows(String tableName, Map<String, Object> paramMap) throws DbException
    {
        return deleteRows(tableName, paramMap, null);
    }

    private void checkRow(Row row)
    {
        if (!row.getIsEffective())
        {
            if (logger.isErrorEnabled())
            {
                logger.error("非法更新sql row:{}", JSON.toJSON(row));
            }
            throw new DbException("找不到对应的table:" + row.getTableName() + ",或参数异常！");
        }
    }

    @Override
    public <T> int update(TableEntity<T> entity) throws DbException
    {
        return update(entity, null);
    }

    @Override
    public <T> int update(List<? extends TableEntity<T>> entityList) throws DbException
    {
        return update(entityList, null);
    }

    @Override
    public <T> int delete(TableEntity<T> entity) throws DbException
    {
        return delete(entity, null);
    }

    @Override
    public <T> int delete(List<? extends TableEntity<T>> enityList) throws DbException
    {
        return delete(enityList, null);
    }

    @Override
    public int update(String tableName, List<Map<String, Object>> fieldMapList) throws DbException
    {
        int count = 0;
        for (Map<String, Object> m : fieldMapList)
        {
            count += update(tableName, m);
        }
        return count;
    }

    @Override
    public int delete(String table, Serializable id) throws DbException
    {
        return delete(table, id, null);
    }

    @Override
    public Row insert(String tableName, Map<String, Object> fieldVals, JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        Row row = RowUtil.getInsertRow(tableName, fieldVals, tmd);
        checkRow(row);
        SqlBean bean = SqlBeanUtil.getInsertSqlBean(row);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(tableName, jdbcTemplate, bean);
        dataDao.executeUpdate(bean);
        return row;
    }

    @Override
    public List<Row> insert(String tableName, List<Map<String, Object>> fList, JdbcTemplate jdbcTemplate)
            throws DbException
    {
        List<Row> list = new ArrayList<Row>(fList.size());

        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        String sql = SqlBeanUtil.getInsertSql(tmd);
        List<Object[]> ps = getInsertMapParamList(fList, tmd, list);
        if (jdbcTemplate == null)
        {
            jdbcTemplate = DbContext.getJdbcTemplateForTable(tableName);
        }
        dataDao.executeBatchUpdate(sql, ps, jdbcTemplate);

        return list;
    }

    private List<Object[]> getInsertMapParamList(List<Map<String, Object>> fList, TableMetaData tmd, List<Row> list)
    {

        List<Column> clist = tmd.getColumnList();
        int len = clist.size();
        List<Object[]> ps = new ArrayList<Object[]>(fList.size());
        TableDefaultValue defaultValue = RowUtil.getTableDefaultValue(tmd);
        Map<String, Object> dmap = null;
        if (defaultValue != null)
        {
            dmap = defaultValue.getNewDefaultValue(tmd);
        }
        Column idColumn = tmd.getIdColumn();
        for (Map<String, Object> e : fList)
        {
            Object[] os = new Object[len];

            Row r = new Row();
            r.setDbType(tmd.getDbType());
            r.setAdditionalMap(dmap);
            r.setIdName(idColumn.getName());
            r.setTableName(tmd.getTableName());
            Map<String, Object> rowValMap = new HashMap<String, Object>(len);
            int i = 0;
            for (Column c : clist)
            {
                Object val = e.get(c.getName());
                if (val == null)
                {
                    val = e.get(c.getColumnName());
                }
                if (c == idColumn)
                {
                    if (val == null || StringUtil.isBlank(val.toString()))
                    {
                        val = IdGeneratorUtil.nextStringId();
                    }
                    r.setIdValue(val.toString());
                }
                else
                {
                    if (val == null && dmap != null)
                    {
                        val = dmap.get(c.getColumnName());
                    }

                }
                os[i++] = val;
                if (val != null)
                {
                    rowValMap.put(c.getName(), val);
                }
            }
            RowUtil.setRowValMap(r, rowValMap);
            ps.add(os);
        }
        return ps;

    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T insert(TableEntity<T> entity, JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(entity.tableName(), jdbcTemplate);
        Row row = RowUtil.getInsertRow(entity, tmd);
        checkRow(row);
        SqlBean bean = SqlBeanUtil.getInsertSqlBean(row);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(entity.tableName(), jdbcTemplate, bean);
        dataDao.executeUpdate(bean);

        Map<String, Object> addMap = row.getAdditionalMap();
        EntryUtil.setFieldValue(entity, row.getIdName(), row.getIdValue());

        for (String key : addMap.keySet())
        {
            Column column = tmd.getColumn(key);
            String name = column.getName();
            if (EntryUtil.getField(entity.getClass(), name) != null)
            {
                EntryUtil.setFieldValue(entity, name, addMap.get(key));
            }
        }
        return (T) entity;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> insert(List<? extends TableEntity<T>> entityList, JdbcTemplate jdbcTemplate) throws DbException
    {

        Map<String, List<TableEntity<T>>> map = new HashMap<String, List<TableEntity<T>>>(2);
        for (TableEntity<T> entity : entityList)
        {
            String tableName = entity.tableName();
            List<TableEntity<T>> list = map.get(tableName);
            if (list == null)
            {
                list = new ArrayList<TableEntity<T>>(entityList.size());
                map.put(tableName, list);
            }
            list.add(entity);
        }
        for (Entry<String, List<TableEntity<T>>> entry : map.entrySet())
        {
            String tableName = entry.getKey();
            List<TableEntity<T>> list = entry.getValue();
            TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
            String sql = SqlBeanUtil.getInsertSql(tmd);
            List<Object[]> ps = getInsertParamList(list, tmd);
            if (jdbcTemplate == null)
            {
                jdbcTemplate = DbContext.getJdbcTemplateForTable(tableName);
            }
            dataDao.executeBatchUpdate(sql, ps, jdbcTemplate);
            for (int i = 0, len = ps.size(); i < len; i++)
            {
                Object[] es = ps.get(0);
                List<Column> clist = tmd.getColumnList();
                TableEntity<T> e = list.get(i);
                for (int j = 0, len2 = clist.size(); j < len2; j++)
                {
                    Object val = es[j];
                    if (val != null)
                    {
                        Column c = clist.get(j);
                        if (EntryUtil.hasField(e, c.getName()))
                        {
                            EntryUtil.setFieldValue(e, c.getName(), val);
                        }
                    }
                }
            }
        }

        return (List<T>) entityList;
    }

    private <T> List<Object[]> getInsertParamList(List<TableEntity<T>> list, TableMetaData tmd)
    {
        List<Column> clist = tmd.getColumnList();
        List<Object[]> ps = new ArrayList<Object[]>(list.size());
        TableDefaultValue defaultValue = RowUtil.getTableDefaultValue(tmd);
        Map<String, Object> dmap = null;
        if (defaultValue != null)
        {
            dmap = defaultValue.getNewDefaultValue(tmd);
        }
        for (TableEntity<T> e : list)
        {
            Object[] os = new Object[clist.size()];
            Column idColumn = tmd.getIdColumn();
            int i = 0;
            for (Column c : clist)
            {
                Object val = EntryUtil.getFieldValue(e, c.getName());
                if (c == idColumn)
                {
                    if (val == null || StringUtil.isBlank(val.toString()))
                    {
                        val = IdGeneratorUtil.nextStringId();
                    }
                }
                else
                {
                    if (val == null && dmap != null)
                    {
                        val = dmap.get(c.getColumnName());
                    }

                }
                os[i++] = val;
            }
            ps.add(os);
        }
        return ps;
    }

    @Override
    public int update(String tableName, Map<String, Object> fieldVals, JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        Row row = RowUtil.getUpdateRow(tableName, fieldVals, tmd);
        checkRow(row);
        SqlBean bean = SqlBeanUtil.getUpdateSqlBean(row);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(tableName, jdbcTemplate, bean);
        return dataDao.executeUpdate(bean);
    }

    @Override
    public int update(String tableName, List<Map<String, Object>> fieldMapList, JdbcTemplate jdbcTemplate)
            throws DbException
    {
        int count = 0;
        for (Map<String, Object> m : fieldMapList)
        {
            count += update(tableName, m, jdbcTemplate);
        }
        return count;
    }

    @Override
    public <T> int update(TableEntity<T> entity, JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(entity.tableName(), jdbcTemplate);
        Row row = RowUtil.getUpdateRow(entity, tmd);
        checkRow(row);
        SqlBean bean = SqlBeanUtil.getUpdateSqlBean(row);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(entity.tableName(), jdbcTemplate, bean);
        return dataDao.executeUpdate(bean);
    }

    @Override
    public <T> int update(List<? extends TableEntity<T>> entityList, JdbcTemplate jdbcTemplate) throws DbException
    {
        int count = 0;
        for (TableEntity<T> e : entityList)
        {
            count += update(e, jdbcTemplate);
        }
        return count;
    }

    @Override
    public int updateRows(String tableName, Map<String, Object> valMap, Map<String, Object> paramMap,
            JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        SqlBean bean = SqlBeanUtil.getUpdateRowsSqlBean(tableName, valMap, paramMap, tmd);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(tableName, jdbcTemplate, bean);
        return dataDao.executeUpdate(bean);
    }

    @Override
    public int delete(String tableName, Map<String, Object> idMap, JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        Row row = RowUtil.getDeleteRow(tableName, idMap, tmd);
        checkRow(row);
        SqlBean bean = SqlBeanUtil.getDeleteSqlBean(row);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(tableName, jdbcTemplate, bean);
        return dataDao.executeUpdate(bean);
    }

    @Override
    public int deleteRows(String tableName, Map<String, Object> paramMap, JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        Row row = RowUtil.getDeleteRows(tableName, paramMap, tmd);
        checkRow(row);
        SqlBean bean = SqlBeanUtil.getDeleteSqlBean(row);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(tableName, jdbcTemplate, bean);
        return dataDao.executeUpdate(bean);
    }

    @Override
    public <T> int delete(TableEntity<T> entity, JdbcTemplate jdbcTemplate) throws DbException
    {
        TableMetaData tmd = DbContext.getTableMetaData(entity.tableName(), jdbcTemplate);
        Row row = RowUtil.getDeleteRow(entity, tmd);
        checkRow(row);
        SqlBean bean = SqlBeanUtil.getDeleteSqlBean(row);
        SqlBeanUtil.checkSqlBeanJdbcTemplate(entity.tableName(), jdbcTemplate, bean);
        return dataDao.executeUpdate(bean);
    }

    @Override
    public <T> int delete(List<? extends TableEntity<T>> enityList, JdbcTemplate jdbcTemplate) throws DbException
    {
        Map<String, List<TableEntity<T>>> map = new HashMap<String, List<TableEntity<T>>>(2);

        for (TableEntity<T> entity : enityList)
        {
            String tableName = entity.tableName();
            List<TableEntity<T>> list = map.get(tableName);
            if (list == null)
            {
                list = new ArrayList<TableEntity<T>>(enityList.size());
                map.put(tableName, list);
            }
            list.add(entity);
        }
        int total = 0;
        for (Entry<String, List<TableEntity<T>>> entry : map.entrySet())
        {
            String tableName = entry.getKey();
            List<TableEntity<T>> l = entry.getValue();
            TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
            String sql = SqlBeanUtil.getDeleteSql(tmd);
            List<Object[]> ps = getIdPs(l, tmd);
            if (jdbcTemplate == null)
            {
                jdbcTemplate = DbContext.getJdbcTemplateForTable(tableName);
            }
            int[] rs = dataDao.executeBatchUpdate(sql, ps, jdbcTemplate);
            total += getTotal(rs);
        }
        return total;
    }

    private <T> List<Object[]> getIdPs(List<TableEntity<T>> l, TableMetaData tmd)
    {
        Column idColumn = tmd.getIdColumn();
        if (idColumn == null)
        {
            throw new DbException("表[" + tmd.getTableName() + "]没有设置主键");
        }
        String name = idColumn.getName();
        List<Object[]> list = new ArrayList<Object[]>(l.size());
        for (TableEntity<T> e : l)
        {
            Object val = EntryUtil.getFieldValue(e, name);
            list.add(new Object[]
            { val });
        }
        return list;
    }

    private int getTotal(int[] rs)
    {
        int t = 0;
        for (int i : rs)
        {
            t += i;
        }
        return t;
    }

    @Override
    public int delete(String tableName, Serializable id, JdbcTemplate jdbcTemplate)
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        String sql = SqlBeanUtil.getDeleteSql(tmd);
        if (jdbcTemplate == null)
        {
            jdbcTemplate = DbContext.getJdbcTemplateForTable(tableName);
        }
        return dataDao.executeUpdate(sql, new Object[]
        { id }, jdbcTemplate);
    }

    @Override
    public int delete(String tableName, List<Map<String, Object>> idMapList)
    {
        return delete(tableName, idMapList, (JdbcTemplate) null);

    }

    @Override
    public int delete(String tableName, List<Map<String, Object>> idMapList, JdbcTemplate jdbcTemplate)
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        List<Object> idList = new ArrayList<Object>(idMapList.size());
        Column idColumn = tmd.getIdColumn();
        for (Map<String, Object> m : idMapList)
        {
            Object val = m.get(idColumn.getName());
            if (val == null)
            {
                val = m.get(idColumn.getColumnName());
            }
            if (val != null && StringUtil.isNotBlank(val.toString()))
            {
                idList.add(val);
            }
        }
        return delete(tableName, idList.toArray(new Serializable[0]), jdbcTemplate);

    }

    @Override
    public int delete(String table, Serializable[] ids) throws DbException
    {
        return delete(table, ids, (JdbcTemplate) null);
    }

    @Override
    public int delete(String tableName, Serializable[] ids, JdbcTemplate jdbcTemplate)
    {
        TableMetaData tmd = DbContext.getTableMetaData(tableName, jdbcTemplate);
        String sql = SqlBeanUtil.getDeleteSql(tmd);
        if (jdbcTemplate == null)
        {
            jdbcTemplate = DbContext.getJdbcTemplateForTable(tableName);
        }
        List<Object[]> plist = new ArrayList<Object[]>(ids.length);
        for (Serializable id : ids)
        {
            plist.add(new Object[]
            { id });
        }
        int[] rs = dataDao.executeBatchUpdate(sql, plist, jdbcTemplate);

        return getTotal(rs);
    }

}
