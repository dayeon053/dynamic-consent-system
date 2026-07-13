package com.consentradar.consentradar.pipeline;

import com.consentradar.consentradar.crawler.CrawlTarget;
import com.consentradar.consentradar.crawler.CrawledPolicyDto;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.repository.CompanyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 임시 실행용 러너 — 카카오 1개 기업 기준으로
 * 크롤링(목업) → 파싱 → 위험도 산출 → DB 저장 파이프라인을 콘솔에서 눈으로 확인하기 위한 용도.
 * 확인이 끝나면 삭제하거나 @Component를 주석 처리해도 된다.
 */
@Component
public class KakaoPipelineDemoRunner implements CommandLineRunner {

    private static final String MOCK_POLICY_TEXT = """
            카카오 개인정보처리방침

            제1조 (수집하는 개인정보 항목)
            카카오는 회원가입, 서비스 이용 과정에서 이름, 휴대폰번호, 이메일 주소, 기기 정보를
            필수 항목으로 수집합니다. 또한 원활한 고객 상담을 위해 상담 이력을 수집할 수 있습니다.

            제2조 (개인정보의 처리 목적)
            수집한 개인정보는 회원 관리, 서비스 제공, 부정 이용 방지 목적으로만 사용되며,
            마케팅 정보 수신에 동의한 회원에 한해 구매 이력 및 관심사 데이터를 활용한
            맞춤형 광고 발송 목적으로도 사용됩니다.

            제3조 (개인정보의 보유 및 이용 기간)
            회원 탈퇴 시까지 보관하며, 전자상거래법 등 관계 법령에 따라 필요한 경우
            해당 법령에서 정한 기간 동안 보관합니다. 마케팅 동의 정보는 동의 철회 시까지 보관합니다.

            제4조 (개인정보의 제3자 제공 및 위탁)
            서비스 운영에 필요한 범위 내에서 계열사 및 광고 파트너사에 일부 정보를 제공할 수 있으며,
            제공받는 자, 제공 목적, 제공 항목은 관련 페이지에 별도로 고지합니다.
            """.repeat(2); // 실제 크롤링 결과와 비슷한 분량을 흉내내기 위한 반복

    private final CompanyRepository companyRepository;
    private final RiskPipelineService riskPipelineService;

    public KakaoPipelineDemoRunner(CompanyRepository companyRepository,
                                    RiskPipelineService riskPipelineService) {
        this.companyRepository = companyRepository;
        this.riskPipelineService = riskPipelineService;
    }

    @Override
    public void run(String... args) {
        System.out.println("=================================================");
        System.out.println(" 카카오 파이프라인 데모 실행 (크롤링 목업 사용)");
        System.out.println("=================================================");

        CrawlTarget target = CrawlTarget.kakao();
        Company company = findOrCreateKakao(target);

        CrawledPolicyDto mockCrawled = new CrawledPolicyDto(
                target.getCompanyName(),
                target.getPolicyUrl(),
                MOCK_POLICY_TEXT,
                OffsetDateTime.now()
        );
        System.out.println("[Demo] 실제 네트워크 요청 없이 목업 크롤링 데이터를 사용합니다. (" +
                mockCrawled.getRawText().length() + "자)");

        List<RiskScore> results = riskPipelineService.runWithCrawledPolicy(target, company, mockCrawled);

        System.out.println("=================================================");
        System.out.println(" 결과 요약 — 저장된 RiskScore " + results.size() + "건");
        for (RiskScore score : results) {
            System.out.printf(" - riskScoreId=%d, totalScore=%s, grade=%s%n",
                    score.getRiskScoreId(), score.getTotalScore(), score.getGrade());
        }
        System.out.println("=================================================");
    }

    private Company findOrCreateKakao(CrawlTarget target) {
        return companyRepository.findByCompanyName(target.getCompanyName())
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setCompanyName(target.getCompanyName());
                    company.setPackageName("com.kakao.talk");
                    company.setPrivacyUrl(target.getPolicyUrl());
                    company.setIsmsCertified(true);
                    return companyRepository.save(company);
                });
    }
}