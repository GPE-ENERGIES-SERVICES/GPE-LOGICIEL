package eu.gpefrance.tenders.response;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResponsePieceRepository extends JpaRepository<ResponsePiece, Long> {

	List<ResponsePiece> findByTenderIdOrderByCategoryAscIdAsc(Long tenderId);

	boolean existsByTenderIdAndLabelKey(Long tenderId, String labelKey);

}
