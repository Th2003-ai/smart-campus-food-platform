package com.campus.delivery.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.ai.entity.KnowledgeBase;
import com.campus.delivery.modules.ai.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客服知识库维护服务。主责：成员3。
 *
 * <p>此前 {@code AdminKnowledgeController} 的保存与删除是空实现（直接返回成功），
 * 管理端改完知识库再问客服，答案不会变。这里补上真正的读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /** 知识库列表（可按分类筛选），命中次数高的排前面 */
    public List<KnowledgeBase> list(String category) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(StringUtils.hasText(category), KnowledgeBase::getCategory, category)
                .orderByDesc(KnowledgeBase::getHitCount)
                .orderByDesc(KnowledgeBase::getCreateTime));
    }

    /**
     * 新增或修改知识。
     *
     * @param knowledge 带 id 为修改，不带 id 为新增
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase save(KnowledgeBase knowledge) {
        if (!StringUtils.hasText(knowledge.getQuestion())) {
            throw new BusinessException(70010, "标准问题不能为空");
        }
        if (!StringUtils.hasText(knowledge.getAnswer())) {
            throw new BusinessException(70011, "标准答案不能为空");
        }
        if (!StringUtils.hasText(knowledge.getKeywords())) {
            // 关键词用于召回，没填时用问题本身兜底，避免这条知识永远检索不到
            knowledge.setKeywords(knowledge.getQuestion());
        }

        if (StringUtils.hasText(knowledge.getId())) {
            KnowledgeBase exists = knowledgeBaseMapper.selectById(knowledge.getId());
            if (exists == null) {
                throw new BusinessException(70012, "知识不存在");
            }
            knowledgeBaseMapper.updateById(knowledge);
            log.info("知识库更新: id={}, question={}", knowledge.getId(), knowledge.getQuestion());
            return knowledge;
        }

        knowledge.setId(null);
        knowledge.setHitCount(0);
        knowledge.setStatus(knowledge.getStatus() == null ? 1 : knowledge.getStatus());
        knowledgeBaseMapper.insert(knowledge);
        log.info("知识库新增: id={}, question={}", knowledge.getId(), knowledge.getQuestion());
        return knowledge;
    }

    /** 删除知识 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String id) {
        KnowledgeBase exists = knowledgeBaseMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(70012, "知识不存在");
        }
        knowledgeBaseMapper.deleteById(id);
        log.info("知识库删除: id={}", id);
    }
}
