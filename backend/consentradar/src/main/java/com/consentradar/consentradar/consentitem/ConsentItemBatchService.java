package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * LLM 파싱 결과로 나온 ConsentItemDto 목록을 하나의 트랜잭션으로 배치 저장한다.
 * 항목 중 하나라도 점수 범위(0~10)를 벗어나면 전체를 저장하지 않고 롤백한다.
 * itemName 기준 upsert({@link ConsentItemUpsertService})라서, 같은 companyId로 두 번
 * 호출돼도(예: 재처리) 기존 항목이 갱신될 뿐 중복 insert되지 않는다.
 */
@Service
public class ConsentItemBatchService {

    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 10.0;

    private final CompanyRepository companyRepository;
    private final ConsentItemUpsertService consentItemUpsertService;

    public ConsentItemBatchService(CompanyRepository companyRepository,
                                    ConsentItemUpsertService consentItemUpsertService) {
        this.companyRepository = companyRepository;
        this.consentItemUpsertService = consentItemUpsertService;
    }

    @Transactional
    public List<ConsentItem> saveAll(Long companyId, List<ConsentItemDto> items) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 companyId: " + companyId));

        items.forEach(this::validateScoreRange);

        return items.stream()
                .map(dto -> consentItemUpsertService.upsert(
                        company, dto.itemName(), dto.itemType(),
                        dto.dsScore(), dto.esScore(), dto.tfScore(), dto.pcScore(), dto.aiScore()))
                .toList();
    }

    private void validateScoreRange(ConsentItemDto dto) {
        validateRange("dsScore", dto.dsScore(), dto.itemName());
        validateRange("esScore", dto.esScore(), dto.itemName());
        validateRange("tfScore", dto.tfScore(), dto.itemName());
        validateRange("pcScore", dto.pcScore(), dto.itemName());
        validateRange("aiScore", dto.aiScore(), dto.itemName());
    }

    private void validateRange(String fieldName, double value, String itemName) {
        if (value < MIN_SCORE || value > MAX_SCORE) {
            throw new InvalidConsentItemScoreException(
                    "[" + itemName + "] " + fieldName + " 값이 0~10 범위를 벗어났습니다: " + value);
        }
    }
}