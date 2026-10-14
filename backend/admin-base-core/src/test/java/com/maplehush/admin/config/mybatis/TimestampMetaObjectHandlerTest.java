package com.maplehush.admin.config.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.maplehush.admin.common.entity.BaseEntity;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class TimestampMetaObjectHandlerTest {
    private final TimestampMetaObjectHandler handler = new TimestampMetaObjectHandler();

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "sample"),
                SampleEntity.class);
    }

    @Test
    void insertFillsSecondPrecisionAndPreservesExplicitCreationTime() {
        SampleEntity entity = new SampleEntity();
        LocalDateTime explicit = LocalDateTime.of(2020, 1, 2, 3, 4, 5);
        entity.setCreateTime(explicit);

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isEqualTo(explicit);
        assertThat(entity.getUpdateTime()).isNotNull();
        assertThat(entity.getUpdateTime().getNano()).isZero();
    }

    @Test
    void updatePreservesExplicitTime() {
        SampleEntity entity = new SampleEntity();
        LocalDateTime explicit = LocalDateTime.of(2020, 1, 2, 3, 4, 5);
        entity.setUpdateTime(explicit);

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateTime()).isEqualTo(explicit);
    }

    private static final class SampleEntity extends BaseEntity {
        @TableId private Long id;
    }
}
