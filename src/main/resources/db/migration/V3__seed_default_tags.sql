-- 기본 태그 시드. 이름·종류 조합 UNIQUE 제약을 위반하지 않도록
-- 이미 같은 (name, type)이 있으면 삽입하지 않는다.

-- 수입 (INCOME)
INSERT INTO tags (name, color, type)
SELECT * FROM (VALUES
    ('급여',         '#10B981', 'INCOME'),
    ('보너스',       '#059669', 'INCOME'),
    ('용돈',         '#34D399', 'INCOME'),
    ('엄마한테 받음', '#F472B6', 'INCOME'),
    ('이자/투자',    '#0EA5E9', 'INCOME'),
    ('환급',         '#22D3EE', 'INCOME'),
    ('기타수입',     '#94A3B8', 'INCOME')
) AS v(name, color, type)
WHERE NOT EXISTS (
    SELECT 1 FROM tags t WHERE t.name = v.name AND t.type = v.type
);

-- 지출 (EXPENSE)
INSERT INTO tags (name, color, type)
SELECT * FROM (VALUES
    ('식비',         '#F97316', 'EXPENSE'),
    ('외식',         '#EA580C', 'EXPENSE'),
    ('카페',         '#D97706', 'EXPENSE'),
    ('장보기',       '#FB923C', 'EXPENSE'),
    ('교통',         '#3B82F6', 'EXPENSE'),
    ('통신',         '#6366F1', 'EXPENSE'),
    ('주거/관리비',  '#8B5CF6', 'EXPENSE'),
    ('공과금',       '#A855F7', 'EXPENSE'),
    ('생필품',       '#14B8A6', 'EXPENSE'),
    ('의류',         '#EC4899', 'EXPENSE'),
    ('미용',         '#F43F5E', 'EXPENSE'),
    ('병원',         '#EF4444', 'EXPENSE'),
    ('약국',         '#DC2626', 'EXPENSE'),
    ('문화/여가',    '#FACC15', 'EXPENSE'),
    ('구독',         '#A3A3A3', 'EXPENSE'),
    ('쇼핑',         '#E11D48', 'EXPENSE'),
    ('여행',         '#06B6D4', 'EXPENSE'),
    ('경조사',       '#7C3AED', 'EXPENSE'),
    ('교육',         '#2563EB', 'EXPENSE'),
    ('자기계발',     '#0891B2', 'EXPENSE'),
    ('선물',         '#DB2777', 'EXPENSE'),
    ('반려동물',     '#84CC16', 'EXPENSE'),
    ('세금',         '#64748B', 'EXPENSE'),
    ('기타지출',     '#9CA3AF', 'EXPENSE')
) AS v(name, color, type)
WHERE NOT EXISTS (
    SELECT 1 FROM tags t WHERE t.name = v.name AND t.type = v.type
);

-- 공통 (BOTH)
INSERT INTO tags (name, color, type)
SELECT * FROM (VALUES
    ('이체',     '#475569', 'BOTH'),
    ('조정',     '#6B7280', 'BOTH')
) AS v(name, color, type)
WHERE NOT EXISTS (
    SELECT 1 FROM tags t WHERE t.name = v.name AND t.type = v.type
);
