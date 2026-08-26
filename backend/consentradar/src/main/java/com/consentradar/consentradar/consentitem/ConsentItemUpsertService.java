package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * itemName 기준으로 ConsentItem을 upsert한다. 기존 항목이 있으면 그 row를 재사용해
 * itemType/ds/es/tf/pc/ai만 갱신하고(UserConsentCheck 등 연관 데이터 보존), 없으면
 * 새로 만든다.
 *
 * DB에는 (company_id, item_name) UNIQUE 제약(V4 마이그레이션)이 걸려 있어 동시 요청이
 * 겹쳐도 중복 row가 실제로 남는 일은 없다는 게 최종적으로 보장된다. "조회 후 insert" 자체는
 * 레이스가 있을 수 있지만(두 트랜잭션이 거의 동시에 "없음"으로 읽고 둘 다 insert 시도),
 * 여기서 그 실패를 잡아서 같은 트랜잭션 안에서 update로 전환하는 시도는 하지 않는다 —
 * 실제로 해보니 Hibernate가 flush 실패 후의 세션을 계속 쓰는 걸 위험하다고 보고
 * `AssertionFailure`(null identifier)를 던지는 걸 확인했다. 그래서 unique 제약 위반은
 * 그대로 던져서 트랜잭션 전체가 롤백되게 둔다 — 레이스에 진 요청은 실패하고 재시도하면
 * 되고, DB에 중복 row가 남는 일은 없다.
 */
@Service
public class ConsentItemUpsertService {

    private final ConsentItemRepository consentItemRepository;

    public ConsentItemUpsertService(ConsentItemRepository consentItemRepository) {
        this.consentItemRepository = consentItemRepository;
    }

    public ConsentItem upsert(Company company, String itemName, ConsentItem.ItemType itemType,
                               int dsScore, int esScore, int tfScore, double pcScore, double aiScore) {
        ConsentItem existing = consentItemRepository
                .findByCompany_CompanyIdAndItemName(company.getCompanyId(), itemName)
                .orElse(null);

        if (existing != null) {
            applyScores(existing, itemType, dsScore, esScore, tfScore, pcScore, aiScore);
            return consentItemRepository.save(existing);
        }

        ConsentItem toInsert = new ConsentItem();
        toInsert.setCompany(company);
        toInsert.setItemName(itemName);
        applyScores(toInsert, itemType, dsScore, esScore, tfScore, pcScore, aiScore);
        return consentItemRepository.save(toInsert);
    }

    private void applyScores(ConsentItem item, ConsentItem.ItemType itemType,
                              int dsScore, int esScore, int tfScore, double pcScore, double aiScore) {
        item.setItemType(itemType);
        item.setDsScore(dsScore);
        item.setEsScore(esScore);
        item.setTfScore(tfScore);
        item.setPcScore(pcScore);
        item.setAiScore(aiScore);
        // 재크롤링에서 다시 매칭됐다는 뜻이므로, 예전에 소프트 삭제(active=false)됐던
        // 항목이 재분석에서 다시 나타나면 되살린다.
        item.setActive(true);
    }

    /**
     * 이번 크롤링/재분석 결과(matchedItemNames)에 더 이상 나타나지 않는 이 기업의 기존
     * 활성 항목을 하드 삭제 대신 소프트 삭제(active=false)한다. UserConsentCheck/
     * UserConsentHistory가 참조하고 있어도 FK가 깨지지 않는다 — 이력은 그대로 보존된다.
     *
     * [TODO 확정 — RiskPipelineService.analyzeAndSaveRisk() 143~147행] "이번 크롤링 결과에
     * 더 이상 나타나지 않는 예전 ConsentItem을 삭제할지, 만료 플래그로 남길지" 결정: 만료
     * 플래그(소프트 삭제)로 확정.
     */
    public void deactivateMissing(Company company, Set<String> matchedItemNames) {
        List<ConsentItem> currentlyActive =
                consentItemRepository.findByCompany_CompanyIdAndActiveTrue(company.getCompanyId());

        for (ConsentItem item : currentlyActive) {
            if (!matchedItemNames.contains(item.getItemName())) {
                item.setActive(false);
                consentItemRepository.save(item);
            }
        }
    }
}
