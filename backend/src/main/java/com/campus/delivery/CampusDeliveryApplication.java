package com.campus.delivery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能 AI 校园外卖平台 · 启动类。
 *
 * <p>模块划分见 docs/02-四人分工与模块归属.md：
 * <ul>
 *   <li>成员1（前端展示层）：frontend 全部文件，四端页面、路由守卫与接口封装</li>
 *   <li>成员2（业务服务层）：各模块的 controller、service、dto，以及 common、security、websocket</li>
 *   <li>成员3（AI 能力层）：modules/ai 的 client、controller、dto、prompt、service，以及 resources/prompts</li>
 *   <li>成员4（数据存储层）：各模块的 entity、mapper，以及 database 脚本、Redis 与文件存储配置</li>
 * </ul>
 *
 * <p>启动后接口文档地址：http://localhost:8080/api/doc.html
 */
@SpringBootApplication
@MapperScan("com.campus.delivery.modules.**.mapper")
@EnableAsync
@EnableScheduling
public class CampusDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusDeliveryApplication.class, args);
    }
}
