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
     * 전체 기업의 약관 스냅샷 중 **실제로 변경이 있었던 것만**(isChanged=true) 확인
     * 시각(crawledAt) 내림차순으로 반환한다(공지사항 탭).
     *
     * [필터링 확정 — api_spec_v2_final.md 결정 사항 1번, 2026-08-25] 이전에는 변경 여부와
     * 무관하게 전체 기업의 최신 스냅샷을 매번 다 반환해서, 변경이 없는 날도 5개 기업이 매일
     * 노출돼 5-1(변경 감지 시 푸시)·5-2(변경 내역 탭)의 기획 의도와 어긋났다. 이제
     * {@link PolicySnapshotRepository#findAllByIsChangedTrueOrderByCrawledAtDesc}로 변경분만
     * 걸러 반환한다. crawledAt은 "변경 시각"이 아니라 "확인 시각"이다 — 변경이 없으면 새
     * 레코드 없이 기존 최신 레코드의 crawledAt만 갱신된다
     * ({@link com.consentradar.consentradar.crawler.PolicyChangeDetectionService#detectAndSave}
     * 참고). 필터링 이후엔 목록에 뜬 항목 자체가 실제 변경 건이라 혼동 여지가 줄어든다.
     */
    @GetMapping("/notices")
    public List<NoticeResponse> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return policySnapshotRepository.findAllByIsChangedTrueOrderByCrawledAtDesc(PageRequest.of(page, size))
                .stream()
                .map(NoticeResponse::from)
                .toList();
    }
}
