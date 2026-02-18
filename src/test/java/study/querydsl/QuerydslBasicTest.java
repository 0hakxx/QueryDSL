package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.MemberDtoNoDefault;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import java.util.List;

import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
class QuerydslBasicTest {
    @PersistenceContext
    private EntityManager em;
    JPAQueryFactory queryFactory;

    @PersistenceUnit
    private EntityManagerFactory emf;

    @BeforeEach
    public void testEntity() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

    }

    @Test
    public void startJPQL() {
        //member1을 찾아라
        String qlString = "select m from Member m where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();
        org.assertj.core.api.Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
        Member findMember = queryFactory.select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        org.assertj.core.api.Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void search() {
        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.between(10, 30)))
                .fetchOne();
        Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultfatch() {
//        List<Member> members = queryFactory.selectFrom(member)
//                .fetch();
//        Member memberOne = queryFactory.selectFrom(member)
//                .fetchOne();
//        Member memberFirst = queryFactory.selectFrom(member)
//                .fetchFirst();
        QueryResults<Member> results = queryFactory.selectFrom(member).fetchResults();
        results.getTotal();
        List<Member> content = results.getResults();
    }

    @Test
    public void orderByTest() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory.selectFrom(member).where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        Assertions.assertThat(member5.getUsername()).isEqualTo("member5");
        Assertions.assertThat(member6.getUsername()).isEqualTo("member6");
        Assertions.assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void pagination() {
        List<Member> result = queryFactory.selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // 0부터 시작
                .limit(2) // 최대 2건 조회
                .fetch();
        Assertions.assertThat(result.size()).isEqualTo(2);
    }
    /*
    팀의 이름과 각 팀의 평균 연령을 구하라.
    * */
    @Test
    public void groupby(){
        List<Tuple> teamResults = queryFactory.select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();
        Tuple teamA = teamResults.get(0);
        Tuple teamB = teamResults.get(1);

        Assertions.assertThat(teamA.get(team.name)).isEqualTo("teamA");
        Assertions.assertThat(teamB.get(team.name)).isEqualTo("teamB");
    }
    @Test
    public void join() {
        QMember member = QMember.member;
        QTeam team = QTeam.team;
        List<Member> results = queryFactory.selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();
        Assertions.assertThat(results.size()).isEqualTo(2);
    }
    @Test
    public void join_on_filtering(){
        List<Tuple> results = queryFactory.select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();
        for (Tuple tuple : results) {
            System.out.println("tuple = " + tuple);
        }
    }
    @Test
    public void left_join_on_no_relation(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        List<Tuple> results = queryFactory.select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();
    }
    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        Assertions.assertThat(loaded).as("페치 조인 미적용").isFalse();
    }
    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory.selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        Assertions.assertThat(loaded).as("페치 조인 적용").isTrue();
    }
    @Test
    // 나이가 가장 많은 회원 조회
    public void subQuery() {
        // 서브쿼리와 메인쿼리의 객체 별칭을 구분하기 위해 따로 생성
        // 메인쿼리: member (static import)
        // 서브쿼리: memberSub (별칭으로 구분)
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory.selectFrom(member)  // 메인쿼리에서 member 사용
                .where(member.age.eq(
                        JPAExpressions.select(memberSub.age.max())  // 서브쿼리에서 memberSub 사용
                                .from(memberSub)
                ))
                .fetch();
        Assertions.assertThat(result).extracting("age").containsExactly(40);
    }
    // 나이가 평균 이상인 회원 조회
    @Test
    public void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions.select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        Assertions.assertThat(result).extracting("age").containsExactly(30, 40);
    }
    //CASE 문
    @Test
    public void caseTest() {
        List<String> result = queryFactory.select(member.age
                        .when(10).then("열살")
                        .when(20).then("열살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
    @Test
    public void caseTest2() {
        NumberExpression<Integer> ranhPath = new CaseBuilder().when(member.age.between(0, 20)).then(2)
                .when(member.age.between(21, 30)).then(1)
                .otherwise(3);
        List<Tuple> results = queryFactory.select(member.username, member.age, ranhPath)
                .from(member)
                .orderBy(ranhPath.desc())
                .fetch();
        for (Tuple tuple : results) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            Integer rank = tuple.get(ranhPath);
            System.out.println("username = " + username + ", age = " + age + ", rank = " + rank);
        }
    }
    @Test
    // 상수
    public void constantTest() {
        List<Tuple> result = queryFactory.select(member.username, member.age, Expressions.constant("A"))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            String constant = tuple.get(Expressions.constant("A"));
            System.out.println("username = " + username + ", age = " + age + ", constant = " + constant);
        }
    }
    @Test
    // 문자 더하기
    public void concatTest() {
        List<String> result = queryFactory.select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
    @Test
    // 프로젝션 대상 하나일 경우
    public void simpleProjection() {
        List<String> result = queryFactory.select(member.username)
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
    // 프로젝션 대상이 둘 이상일 경우
    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory.select(member.username, member.age)
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username + ", age = " + age);
        }
    }
    //Member DTO 변환
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }
    // DTO로 변환 - Setter
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory.select(Projections.bean(MemberDto.class, member.username, member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }
    // DTO로 변환 - Field
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory.select(Projections.fields(MemberDto.class, member.username, member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 기본 생성자 없을 때 테스트
     * - Setter 방식: 기본 생성자 필요 (없으면 에러)
     * - Field 방식: 기본 생성자 필요 (없으면 에러)
     */
    @Test
    public void findDtoNoDefaultConstructor_Setter() {
        // 기본 생성자가 없으면 실행 시 에러 발생
        // Exception: No default constructor for entity 'study.querydsl.dto.MemberDtoNoDefault'
        try {
            List<MemberDtoNoDefault> result = queryFactory
                    .select(Projections.bean(MemberDtoNoDefault.class, member.username, member.age))
                    .from(member)
                    .fetch();
            System.out.println("Setter 방식 성공 (기본 생성자 없음)");
        } catch (Exception e) {
            System.out.println("❌ Setter 방식 실패: " + e.getMessage());
            System.out.println("이유: 기본 생성자가 없어서 객체 생성 불가");
        }
    }

    @Test
    public void findDtoNoDefaultConstructor_Field() {
        // 기본 생성자가 없으면 실행 시 에러 발생
        // Exception: No default constructor for entity 'study.querydsl.dto.MemberDtoNoDefault'
        try {
            List<MemberDtoNoDefault> result = queryFactory
                    .select(Projections.fields(MemberDtoNoDefault.class, member.username, member.age))
                    .from(member)
                    .fetch();
            System.out.println("Field 방식 성공 (기본 생성자 없음)");
        } catch (Exception e) {
            System.out.println("❌ Field 방식 실패: " + e.getMessage());
            System.out.println("이유: 기본 생성자가 없어서 객체 생성 불가");
        }
    }

    /**
     * 정리:
     * 1. Projections.bean() - Setter 방식
     *    - 기본 생성자로 객체 생성 → Setter 메서드로 값 주입
     *    - 기본 생성자 필수!
     *    - Setter 메서드 필수!
     *
     * 2. Projections.fields() - Field 방식
     *    - 기본 생성자로 객체 생성 → Reflection으로 필드에 직접 값 주입
     *    - 기본 생성자 필수!
     *    - Setter 메서드 불필요!
     *
     * 3. Projections.constructor() - 생성자 방식
     *    - 파라미터 생성자로 직접 객체 생성
     *    - 기본 생성자 불필요!
     */
    @Test
    public void findDtoByConstructor_NoDefaultNeeded() {
                // 생성자 방식은 기본 생성자 없어도 동작!
                List<MemberDtoNoDefault> result = queryFactory
                        .select(Projections.constructor(MemberDtoNoDefault.class, member.username, member.age))
                        .from(member)
                        .fetch();
                for (MemberDtoNoDefault dto : result) {
                    System.out.println("✅ 생성자 방식 성공: " + dto);
        }
    }

    @Test
    public void findUserDtoByField() {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory.select(Projections.fields(UserDto.class,
                        member.username.as("name"), // 별칭 매핑
                        Expressions.as(
                                JPAExpressions
                                        .select(memberSub.age.max())
                                        .from(memberSub), "age")
                )
                ).from(member)
                .fetch();
        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }
    // QueuryProjections.constructor() - 생성자 방식
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> results = queryFactory.select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();
    }
    // 동적 쿼리 - BooleanBuilder
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameParam, Integer ageParam) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if(usernameParam != null) {
            booleanBuilder.and(member.username.eq(usernameParam));
        }
        if(ageParam != null) {
            booleanBuilder.and(member.age.eq(ageParam));
        }


        List<Member> results = queryFactory.selectFrom(member)
                .where(booleanBuilder)
                .fetch();
        return results;
    }
    // 다중 where 쿼리문
    @Test
    public void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {
        return queryFactory.selectFrom(member)
                .where(usernameEq(usernameParam), ageEq(ageParam))
                .fetch();
    }

    private BooleanExpression ageEq(Integer ageParam) {
        if(ageParam != null) {
            return member.age.eq(ageParam);
        }
        else return null;
    }

    private BooleanExpression usernameEq(String usernameParam) {
        if(usernameParam != null) {
            return member.username.eq(usernameParam);
        }
        else return null;
    }

    /**
     * 벌크 연산 - 정상 케이스
     *
     * <p>벌크 연산은 영속성 컨텍스트를 무시하고 DB에 직접 쿼리를 실행합니다.</p>
     */
    @Test
    @Commit
    public void bulkUpdate() {
        long count = queryFactory.update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
        Assertions.assertThat(count).isEqualTo(2);
    }
    //function Member -> m 으로 변경
    @Test
    public void function() {
        String result = queryFactory .
                select(Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetchFirst();
        System.out.println("result = " + result);
    }

}
