package com.consentradar.consentradar.crawler;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 새로 크롤링한 raw_text를 해당 기업의 최신 PolicySnapshot과 SHA-256 해시로 비교해
 * 변경 여부를 판단하고 그 결과에 따라 저장한다.
 *
 * - 최신 스냅샷이 없음(최초 수집): 새 레코드 insert, is_changed=false
 * - 해시가 다름(변경 있음): 새 레코드 insert, is_changed=true
 * - 해시가 같음(변경 없음): 새 레코드를 만들지 않고 기존 최신 레코드의 crawled_at만 갱신
 */
@Service
public class PolicyChangeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(PolicyChangeDetectionService.class);

    private final PolicySnapshotRepository policySnapshotRepository;

    public PolicyChangeDetectionService(PolicySnapshotRepository policySnapshotRepository) {
        this.policySnapshotRepository = policySnapshotRepository;
    }

    @Transactional
    public PolicySnapshot detectAndSave(Company company, String rawText) {
        String newHash = PolicyTextHasher.sha256(rawText);

        Optional<PolicySnapshot> latest =
                policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(company.getCompanyId());

        if (latest.isEmpty()) {
            log.info("[ChangeDetection] {} 최초 수집 — 기준 스냅샷 저장", company.getCompanyName());
            return insertNewSnapshot(company, rawText, false);
        }

        String previousHash = PolicyTextHasher.sha256(latest.get().getRawText());

        if (!newHash.equals(previousHash)) {
            log.info("[ChangeDetection] {} 약관 변경 감지 — 새 스냅샷 저장", company.getCompanyName());
            return insertNewSnapshot(company, rawText, true);
        }

        PolicySnapshot existing = latest.get();
        existing.setCrawledAt(LocalDateTime.now());
        log.info("[ChangeDetection] {} 변경 없음 — crawled_at만 갱신", company.getCompanyName());
        return policySnapshotRepository.save(existing);
    }

    private PolicySnapshot insertNewSnapshot(Company company, String rawText, boolean changed) {
        PolicySnapshot snapshot = new PolicySnapshot();
        snapshot.setCompany(company);
        snapshot.setRawText(rawText);
        snapshot.setChanged(changed);
        return policySnapshotRepository.save(snapshot);
    }
}