package com.campus.delivery.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.common.util.MaskUtil;
import com.campus.delivery.modules.user.dto.AddressDTO;
import com.campus.delivery.modules.user.dto.DietProfileDTO;
import com.campus.delivery.modules.user.dto.UserProfileVO;
import com.campus.delivery.modules.user.entity.DietProfile;
import com.campus.delivery.modules.user.entity.Student;
import com.campus.delivery.modules.user.entity.SysUser;
import com.campus.delivery.modules.user.entity.UserAddress;
import com.campus.delivery.modules.user.mapper.DietProfileMapper;
import com.campus.delivery.modules.user.mapper.StudentMapper;
import com.campus.delivery.modules.user.mapper.SysUserMapper;
import com.campus.delivery.modules.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户与画像服务。主责：成员1。
 *
 * <p>覆盖需求文档 5.1.7 个人中心与饮食分析、5.6.5 用户画像与推荐冷启动的数据基础。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final DietProfileMapper dietProfileMapper;
    private final UserAddressMapper userAddressMapper;

    /** 个人中心信息：账号 + 学生档案 + 饮食档案，敏感字段脱敏后返回 */
    public UserProfileVO getProfile(String userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(10005, "用户不存在");
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(MaskUtil.phone(user.getPhone()));
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());

        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, userId).last("LIMIT 1"));
        if (student != null) {
            vo.setStudentNo(student.getStudentNo());
            vo.setCollege(student.getCollege());
            vo.setGrade(student.getGrade());
        }

        DietProfile profile = getOrCreateDietProfile(userId);
        vo.setDietGoal(profile.getDietGoal());
        vo.setTastePreference(profile.getTastePreference());
        vo.setAllergyFoods(profile.getAllergyFoods());
        return vo;
    }

    /** 查询饮食档案（不存在时自动创建空白档案，保证冷启动可用） */
    public DietProfile getOrCreateDietProfile(String userId) {
        DietProfile profile = dietProfileMapper.selectOne(new LambdaQueryWrapper<DietProfile>()
                .eq(DietProfile::getUserId, userId).last("LIMIT 1"));
        if (profile == null) {
            profile = new DietProfile();
            profile.setUserId(userId);
            profile.setDietGoal(0);
            dietProfileMapper.insert(profile);
        }
        return profile;
    }

    /** 维护饮食档案 */
    @Transactional(rollbackFor = Exception.class)
    public DietProfile updateDietProfile(String userId, DietProfileDTO dto) {
        DietProfile profile = getOrCreateDietProfile(userId);
        profile.setDietGoal(dto.getDietGoal());
        profile.setTastePreference(dto.getTastePreference());
        profile.setAllergyFoods(dto.getAllergyFoods());
        profile.setDislikeFoods(dto.getDislikeFoods());
        profile.setHeightCm(dto.getHeightCm());
        profile.setWeightKg(dto.getWeightKg());
        dietProfileMapper.updateById(profile);
        return profile;
    }

    // ------------------------------------------------------------------
    // 账号状态（供管理端封禁/解封，以及审核列表展示）
    // ------------------------------------------------------------------

    /**
     * 账号状态：0禁用 1正常；账号不存在返回 null。
     *
     * <p>封禁语义统一落在 {@code sys_user.status} 上：登录入口
     * （{@code AuthService.login}）已经拦截 status = 0，因此置 0 即可阻止该账号继续登录。
     */
    public Integer getAccountStatus(String userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getStatus();
    }

    /** 批量查账号状态：userId -> status，避免审核列表逐条查询 */
    public Map<String, Integer> mapAccountStatus(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<String, Integer> result = new HashMap<>(users.size());
        for (SysUser user : users) {
            result.put(user.getId(), user.getStatus());
        }
        return result;
    }

    /** 启用/禁用账号：0禁用 1正常。用于管理端封禁与解封 */
    @Transactional(rollbackFor = Exception.class)
    public void updateAccountStatus(String userId, Integer status) {
        SysUser exists = sysUserMapper.selectById(userId);
        if (exists == null) {
            throw new BusinessException(10005, "用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        sysUserMapper.updateById(update);
    }

    // ------------------------------------------------------------------
    // 收货地址
    // ------------------------------------------------------------------

    public List<UserAddress> listAddress(String userId) {
        return userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddress saveAddress(String userId, AddressDTO dto) {
        UserAddress address = new UserAddress();
        address.setId(dto.getId());
        address.setUserId(userId);
        address.setReceiver(dto.getReceiver());
        address.setPhone(dto.getPhone());
        address.setCampusArea(dto.getCampusArea());
        address.setBuilding(dto.getBuilding());
        address.setDetail(dto.getDetail());
        address.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()) ? 1 : 0);

        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            clearDefault(userId);
        }

        if (dto.getId() == null || dto.getId().isEmpty()) {
            userAddressMapper.insert(address);
        } else {
            // 校验归属，防止越权修改他人地址
            UserAddress exists = userAddressMapper.selectById(dto.getId());
            if (exists == null || !userId.equals(exists.getUserId())) {
                throw new BusinessException(10006, "地址不存在或无权修改");
            }
            userAddressMapper.updateById(address);
        }
        return address;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(String userId, String addressId) {
        UserAddress exists = userAddressMapper.selectById(addressId);
        if (exists == null || !userId.equals(exists.getUserId())) {
            throw new BusinessException(10006, "地址不存在或无权删除");
        }
        userAddressMapper.deleteById(addressId);
    }

    private void clearDefault(String userId) {
        UserAddress reset = new UserAddress();
        reset.setIsDefault(0);
        userAddressMapper.update(reset, new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
    }
}
