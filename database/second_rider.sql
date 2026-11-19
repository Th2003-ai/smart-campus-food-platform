-- 骑手B（第二个骑手）：用于两个骑手同时在线抢单演示，密码 Rid@Campus2026
-- 注意：U004 是 admin，骑手B 使用 U005
INSERT INTO sys_user (id, username, password, role, nickname, phone, status, deleted)
VALUES ('U000000000000000000000000000005', '13700000002',
        '$2a$10$A.EcFPsE0o0R.pKYtzllMOKZ87xsRfMxLhaudJUhrMTLYB4e.czXu',
        'RIDER', '演示骑手2', '13700000002', 1, 0);

-- 修正骑手B档案（先前误关联到 admin U004，改挂到骑手B U005）
UPDATE rider
SET user_id='U000000000000000000000000000005', real_name='演示骑手2',
    vehicle_type='电动车', work_status=1, delivering_count=0,
    max_delivering=3, total_income=0.00, audit_status=1
WHERE id='R000000000000000000000000000002';
