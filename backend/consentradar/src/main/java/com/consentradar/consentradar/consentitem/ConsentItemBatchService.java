package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * LLM 파싱 결과로 나온 ConsentItemDto 목록을 하나의 트랜잭션으로 배치 저장한다.
 * 항목 중 하나라도 점수 범위(0~10)를 벗어나면 전체를 저장하지 않고 롤백한다.
 */
@Service
public class ConsentItemBatchService {

    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 10.0;

    private final CompanyRepository companyRepository;
    private final ConsentItemRepository consentItemRepository;

    public ConsentItemBatchService(CompanyRepository companyRepository,
                                    ConsentItemRepository consentItemRepository) {
        this.companyRepository = companyRepository;
        this.consentItemRepository = consentItemRepository;
    }

    @Transactional
    public List<ConsentItem> saveAll(Long companyId, List<ConsentItemDto> items) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 companyId: " + companyId));

        items.forEach(this::validateScoreRange);

        List<ConsentItem> entities = items.stream()
                .map(dto -> toEntity(company, dto))
                .toList();

        return consentItemRepository.saveAll(entities);
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

    private ConsentItem toEntity(Company company, ConsentItemDto dto) {
        ConsentItem entity = new ConsentItem();
        entity.setCompany(company);
        entity.setItemType(dto.itemType());
        entity.setItemName(dto.itemName());
        entity.setDsScore(dto.dsScore());
        entity.setEsScore(dto.esScore());
        entity.setTfScore(dto.tfScore());
        entity.setPcScore(dto.pcScore());
        entity.setAiScore(dto.aiScore());
        return entity;
    }
}