package com.consentradar.consentradar.notice;

import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class NoticeController {

    private final PolicySnapshotRepository policySnapshotRepository;

    public NoticeController(PolicySnapshotRepository policySnapshotRepository) {
        this.policySnapshotRepository = policySnapshotRepository;
    }

    /**
     * GET /notices?page=0&size=20
     * 전체 기업의 약관 스냅샷을 확인 시각(crawledAt) 내림차순으로 반환한다(공지사항 탭).
     * crawledAt은 "변경 시각"이 아니라 "확인 시각"이다 — 변경이 없어도 매 크롤링마다 갱신된다
     * ({@link com.consentradar.consentradar.crawler.PolicyChangeDetectionService#detectAndSave}
     * 참고, 변경 없으면 기존 최신 레코드의 crawledAt만 갱신).
     */
    @GetMapping("/notices")
    public List<NoticeResponse> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return policySnapshotRepository.findAllByOrderByCrawledAtDesc(PageRequest.of(page, size))
                .stream()
                .map(NoticeResponse::from)
                .toList();
    }
}
