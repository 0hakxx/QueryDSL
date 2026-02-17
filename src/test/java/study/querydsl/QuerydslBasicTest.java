package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
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
import org.springframework.transaction.annotation.Transactional;
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
}