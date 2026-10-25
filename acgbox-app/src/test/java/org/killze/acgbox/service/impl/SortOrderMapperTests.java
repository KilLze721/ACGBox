package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.entity.content.Alias;
import org.killze.acgbox.entity.content.BroadcastType;
import org.killze.acgbox.entity.content.CompanyRelation;
import org.killze.acgbox.mapper.AdaptationTypeMapper;
import org.killze.acgbox.mapper.AliasMapper;
import org.killze.acgbox.mapper.BroadcastTypeMapper;
import org.killze.acgbox.mapper.CompanyRelationMapper;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用独立 SQLite 内存数据库验证真实 MyBatis-Plus 映射及排序 SQL，不读取应用数据源配置。
 */
class SortOrderMapperTests {

    private Connection connection;
    private SqlSession session;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            for (String table : List.of("broadcast_type", "adaptation_type")) {
                statement.execute("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, "
                        + "sort_order INTEGER NOT NULL DEFAULT 0, created_at TEXT, updated_at TEXT)");
            }
            statement.execute("CREATE TABLE alias (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, "
                    + "target_id INTEGER, alias_name TEXT, sort_order INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE company_relation (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, "
                    + "target_id INTEGER, company_id INTEGER, role TEXT, sort_order INTEGER NOT NULL DEFAULT 0)");
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("sort-order-memory", new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        configuration.addMapper(BroadcastTypeMapper.class);
        configuration.addMapper(AdaptationTypeMapper.class);
        configuration.addMapper(AliasMapper.class);
        configuration.addMapper(CompanyRelationMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void broadcastMapsWritesDefaultsUpdatesAndStableSort() {
        BroadcastTypeMapper mapper = session.getMapper(BroadcastTypeMapper.class);
        BroadcastType first = BroadcastType.builder().name("Z").build();
        BroadcastType second = BroadcastType.builder().name("A").sortOrder(0L).build();
        BroadcastType third = BroadcastType.builder().name("B").sortOrder(-1L).build();
        mapper.insert(first);
        mapper.insert(second);
        mapper.insert(third);
        assertEquals(0L, mapper.selectById(first.getId()).getSortOrder());
        var result = mapper.selectList(new LambdaQueryWrapper<BroadcastType>()
                .orderByAsc(BroadcastType::getSortOrder).orderByAsc(BroadcastType::getId));
        assertEquals(List.of(third.getId(), first.getId(), second.getId()), result.stream().map(BroadcastType::getId).toList());
        first.setSortOrder(-2147483648L);
        mapper.updateById(first);
        assertEquals(-2147483648L, mapper.selectById(first.getId()).getSortOrder());
    }

    @Test
    void adaptationMapsWritesDefaultsUpdatesAndStableSort() {
        AdaptationTypeMapper mapper = session.getMapper(AdaptationTypeMapper.class);
        AdaptationType first = AdaptationType.builder().name("Z").build();
        AdaptationType second = AdaptationType.builder().name("A").sortOrder(0L).build();
        AdaptationType third = AdaptationType.builder().name("B").sortOrder(-1L).build();
        mapper.insert(first);
        mapper.insert(second);
        mapper.insert(third);
        assertEquals(0L, mapper.selectById(first.getId()).getSortOrder());
        var result = mapper.selectList(new LambdaQueryWrapper<AdaptationType>()
                .orderByAsc(AdaptationType::getSortOrder).orderByAsc(AdaptationType::getId));
        assertEquals(List.of(third.getId(), first.getId(), second.getId()), result.stream().map(AdaptationType::getId).toList());
        first.setSortOrder(2147483647L);
        mapper.updateById(first);
        assertEquals(2147483647L, mapper.selectById(first.getId()).getSortOrder());
    }

    @Test
    void aliasMapsStoredSortAndFiltersOtherTargets() {
        AliasMapper mapper = session.getMapper(AliasMapper.class);
        Alias first = Alias.builder().targetType("ANIME").targetId(1L).aliasName("Z").build();
        Alias second = Alias.builder().targetType("ANIME").targetId(1L).aliasName("A").sortOrder(0L).build();
        Alias third = Alias.builder().targetType("ANIME").targetId(1L).aliasName("B").sortOrder(-1L).build();
        mapper.insert(first);
        mapper.insert(second);
        mapper.insert(third);
        mapper.insert(Alias.builder().targetType("BOOK").targetId(1L).aliasName("其他类型").sortOrder(-9L).build());
        mapper.insert(Alias.builder().targetType("ANIME").targetId(2L).aliasName("其他动画").sortOrder(-9L).build());
        var result = mapper.selectList(new LambdaQueryWrapper<Alias>().eq(Alias::getTargetType, "ANIME")
                .eq(Alias::getTargetId, 1L).orderByAsc(Alias::getSortOrder).orderByAsc(Alias::getId));
        assertEquals(List.of("B", "Z", "A"), result.stream().map(Alias::getAliasName).toList());
        assertEquals(List.of(-1L, 0L, 0L), result.stream().map(Alias::getSortOrder).toList());
    }

    @Test
    void companyRelationMapsSortAndUsesRelationIdNotCompanyId() {
        CompanyRelationMapper mapper = session.getMapper(CompanyRelationMapper.class);
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(1L).companyId(9L).role("制作").sortOrder(0L).build());
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(1L).companyId(2L).role("发行").build());
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(1L).companyId(3L).role("协力").sortOrder(-1L).build());
        mapper.insert(CompanyRelation.builder().targetType("BOOK").targetId(1L).companyId(4L).sortOrder(-9L).build());
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(2L).companyId(5L).sortOrder(-9L).build());
        var result = mapper.selectList(new LambdaQueryWrapper<CompanyRelation>().eq(CompanyRelation::getTargetType, "ANIME")
                .in(CompanyRelation::getTargetId, List.of(1L)).orderByAsc(CompanyRelation::getSortOrder).orderByAsc(CompanyRelation::getId));
        assertEquals(List.of(3L, 9L, 2L), result.stream().map(CompanyRelation::getCompanyId).toList());
        assertEquals(List.of(-1L, 0L, 0L), result.stream().map(CompanyRelation::getSortOrder).toList());
    }
}
