package com.busanit501.teamboot.service;

import com.busanit501.teamboot.domain.MatchingRoom;
import com.busanit501.teamboot.domain.Pet;
import com.busanit501.teamboot.domain.RoomParticipant;
import com.busanit501.teamboot.domain.Member;
import com.busanit501.teamboot.dto.MatchingRoomDTO;
import com.busanit501.teamboot.dto.PetDTO;
import com.busanit501.teamboot.dto.MemberDTO;
import com.busanit501.teamboot.exception.ResourceNotFoundException;
import com.busanit501.teamboot.repository.MatchingRoomRepository;
import com.busanit501.teamboot.repository.PetRepository;
import com.busanit501.teamboot.repository.RoomParticipantRepository;
import com.busanit501.teamboot.repository.MemberRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MatchingService 클래스
 * - 매칭방 관련 비즈니스 로직을 처리.
 * - MatchingRoomRepository, RoomParticipantRepository, PetRepository, MemberRepository를 통해 데이터 접근.
 */
@Log4j2
@Service
public class MatchingService {

    private final MatchingRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final PetRepository petRepository;
    private final MemberRepository memberRepository;

    /**
     * 생성자 주입 방식으로 레포지토리 초기화
     */
    @Autowired
    public MatchingService(MatchingRoomRepository roomRepository,
                           RoomParticipantRepository participantRepository,
                           PetRepository petRepository,
                           MemberRepository memberRepository) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.petRepository = petRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * 모든 매칭방 리스트 조회
     *
     * @return 매칭방 DTO 리스트
     */
    public List<MatchingRoomDTO> getAllRooms() {
        List<MatchingRoom> rooms = roomRepository.findAll();
        return rooms.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 특정 ID에 해당하는 매칭방 조회
     *
     * @param roomId 매칭방 ID
     * @return MatchingRoom 객체
     */
    public MatchingRoom getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("매칭방을 찾을 수 없습니다. ID: " + roomId));
    }

    /**
     * 매칭방 생성 로직
     *
     * @param dto         매칭방 정보를 담은 DTO
     * @param mhostMember  매칭방 호스트 회원
     */
    @Transactional
    public void createRoom(MatchingRoomDTO dto, Member mhostMember) {
        MatchingRoom room = new MatchingRoom();
        room.setMhost(mhostMember);
        room.setTitle(dto.getTitle());
        room.setDescription(dto.getDescription());
        room.setPlace(dto.getPlace());
        room.setMeetingDate(dto.getMeetingDate());
        room.setMeetingTime(dto.getMeetingTime());
        room.setMaxParticipants(dto.getMaxParticipants());
        room.setImageUrl(dto.getImageUrl());

        MatchingRoom savedRoom = roomRepository.save(room);

        // 호스트의 반려동물 참가자로 등록
        List<Pet> pets = petRepository.findAllById(dto.getPetIds());
        for (Pet pet : pets) {
            RoomParticipant participant = new RoomParticipant();
            participant.setMatchingRoom(savedRoom);
            participant.setMember(mhostMember);
            participant.setPet(pet);
            participant.setStatus(RoomParticipant.ParticipantStatus.Accepted);
            participantRepository.save(participant);
        }
    }

    /**
     * 매칭방 수정 로직
     *
     * @param roomId      수정 대상 매칭방 ID
     * @param dto         수정 정보를 담은 DTO
     * @param mhostMember  매칭방 호스트 회원
     */
    @Transactional
    public void updateRoom(Long roomId, MatchingRoomDTO dto, Member mhostMember) {
        MatchingRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("매칭방을 찾을 수 없습니다."));

        if (!room.getMhost().getMid().equals(mhostMember.getMid())) {
            throw new RuntimeException("방장만 수정할 수 있습니다.");
        }

        room.setTitle(dto.getTitle());
        room.setDescription(dto.getDescription());
        room.setPlace(dto.getPlace());
        room.setMeetingDate(dto.getMeetingDate());
        room.setMeetingTime(dto.getMeetingTime());
        room.setMaxParticipants(dto.getMaxParticipants());

        // 기존 호스트 반려동물 정보 삭제 후 재등록
        List<RoomParticipant> existingParticipants =
                participantRepository.findAllByMatchingRoomAndMember(room, mhostMember);
        participantRepository.deleteAll(existingParticipants);

        List<Pet> pets = petRepository.findAllById(dto.getPetIds());
        for (Pet pet : pets) {
            RoomParticipant participant = new RoomParticipant();
            participant.setMatchingRoom(room);
            participant.setMember(mhostMember);
            participant.setPet(pet);
            participant.setStatus(RoomParticipant.ParticipantStatus.Accepted);
            participantRepository.save(participant);
        }
    }

    /**
     * 매칭방 참가 신청 로직
     *
     * @param roomId    신청 대상 매칭방 ID
     * @param mid  신청 회원 ID
     * @param petIds    참가 신청 반려동물 ID 리스트
     */
    @Transactional
    public void applyRoom(Long roomId, String mid, List<Long> petIds) {
        MatchingRoom room = getRoomById(roomId);
        Member member = memberRepository.findById(mid)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));

        if (!participantRepository.findAllByMatchingRoomAndMember(room, member).isEmpty()) {
            throw new RuntimeException("이미 참가 신청을 했습니다.");
        }

        List<Pet> pets = petRepository.findAllById(petIds);
        if (pets.size() != petIds.size()) {
            throw new ResourceNotFoundException("일부 반려동물을 찾을 수 없습니다.");
        }

        long acceptedCount = participantRepository.countDistinctMemberByMatchingRoomAndStatus(
                room, RoomParticipant.ParticipantStatus.Accepted);
        if (acceptedCount + 1 > room.getMaxParticipants()) {
            throw new RuntimeException("참가 인원이 초과되었습니다.");
        }

        for (Pet pet : pets) {
            RoomParticipant participant = new RoomParticipant();
            participant.setMatchingRoom(room);
            participant.setMember(member);
            participant.setPet(pet);
            participant.setStatus(RoomParticipant.ParticipantStatus.Pending);
            participantRepository.save(participant);
        }
    }

    /**
     * 참가 신청 승인 처리
     *
     * @param roomId    매칭방 ID
     * @param mid  승인 대상 회원 ID
     */
    @Transactional
    public void acceptParticipant(Long roomId, String mid) {
        MatchingRoom room = getRoomById(roomId);
        Member member = memberRepository.findById(mid)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));

        List<RoomParticipant> participants = participantRepository.findAllByMatchingRoomAndMember(room, member);
        if (participants.isEmpty()) {
            throw new ResourceNotFoundException("참가 신청을 찾을 수 없습니다.");
        }

        long acceptedCount = participantRepository.countDistinctMemberByMatchingRoomAndStatus(
                room, RoomParticipant.ParticipantStatus.Accepted);
        if (acceptedCount + 1 > room.getMaxParticipants()) {
            throw new RuntimeException("최대 참가 인원을 초과하여 승인할 수 없습니다.");
        }

        for (RoomParticipant participant : participants) {
            participant.setStatus(RoomParticipant.ParticipantStatus.Accepted);
            participantRepository.save(participant);
        }
    }

    /**
     * 참가 신청 거절 처리
     *
     * @param roomId    매칭방 ID
     * @param mid  거절 대상 회원 ID
     */
    @Transactional
    public void rejectParticipant(Long roomId, String mid) {
        MatchingRoom room = getRoomById(roomId);
        Member member = memberRepository.findById(mid)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));

        List<RoomParticipant> participants = participantRepository.findAllByMatchingRoomAndMember(room, member);
        if (participants.isEmpty()) {
            throw new ResourceNotFoundException("참가 신청을 찾을 수 없습니다.");
        }

        for (RoomParticipant participant : participants) {
            participant.setStatus(RoomParticipant.ParticipantStatus.Rejected);
            participantRepository.save(participant);
        }
    }

    /**
     * 매칭방의 참가자 정보 조회
     *
     * @param roomId 매칭방 ID
     * @return 참가자 목록
     */
    public List<RoomParticipant> getParticipantsByRoomId(Long roomId) {
        return participantRepository.findAllByMatchingRoom_RoomId(roomId);
    }

    /**
     * 승인된 참가자와 반려동물 정보 반환
     *
     * @param room 매칭방 객체
     * @return Member와 Pet 리스트를 매핑한 Map
     */
    public Map<Member, List<Pet>> getAcceptedMemberPets(MatchingRoom room) {
        List<RoomParticipant> accepted = participantRepository.findAllByMatchingRoomAndStatus(
                room, RoomParticipant.ParticipantStatus.Accepted);

        Map<Member, List<Pet>> map = new LinkedHashMap<>();
        for (RoomParticipant rp : accepted) {
            Member member = rp.getMember();
            Pet pet = rp.getPet();
            map.computeIfAbsent(member, k -> new ArrayList<>()).add(pet);
        }
        return map;
    }

    /**
     * 대기 중인 참가자와 반려동물 정보 반환
     *
     * @param room 매칭방 객체
     * @return Member와 Pet 리스트를 매핑한 Map
     */
    @Transactional(readOnly = true)
    public Map<Member, List<Pet>> getPendingMemberPets(MatchingRoom room) {
        List<RoomParticipant> pending = participantRepository.findAllByMatchingRoomAndStatus(
                room, RoomParticipant.ParticipantStatus.Pending);

        Map<Member, List<Pet>> map = new LinkedHashMap<>();
        for (RoomParticipant rp : pending) {
            Member member = rp.getMember();
            Pet pet = rp.getPet();
            map.computeIfAbsent(member, k -> new ArrayList<>()).add(pet);
        }
        return map;
    }

    /**
     * RoomParticipant 리스트 필터링
     *
     * @param participants 참가자 리스트
     * @param status       필터링할 상태
     * @return 필터링된 참가자 리스트
     */
    public List<RoomParticipant> filterParticipants(List<RoomParticipant> participants,
                                                    RoomParticipant.ParticipantStatus status) {
        return participants.stream()
                .filter(p -> p.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * MatchingRoom 객체를 DTO로 변환
     *
     * @param room 매칭방 객체
     * @return MatchingRoomDTO 객체
     */
    public MatchingRoomDTO convertToDto(MatchingRoom room) {
        MatchingRoomDTO dto = new MatchingRoomDTO();
        dto.setRoomId(room.getRoomId());
        dto.setTitle(room.getTitle());
        dto.setDescription(room.getDescription());
        dto.setPlace(room.getPlace());
        dto.setMeetingDate(room.getMeetingDate());
        dto.setMeetingTime(room.getMeetingTime());
        dto.setMaxParticipants(room.getMaxParticipants());
        dto.setImageUrl(room.getImageUrl());

        // 호스트 펫 ID 리스트 추가
        List<Long> petIds = room.getParticipants().stream()
                .filter(p -> p.getMember().getMid().equals(room.getMhost().getMid()))
                .map(p -> p.getPet().getPetId())
                .collect(Collectors.toList());
        dto.setPetIds(petIds);

        // 참여자 펫 정보 리스트 추가
        List<PetDTO> pets = room.getParticipants().stream()
                .map(participant -> {
                    PetDTO petDTO = new PetDTO();
                    petDTO.setPetId(participant.getPet().getPetId());
                    petDTO.setName(participant.getPet().getName());
                    petDTO.setType(participant.getPet().getType());
                    petDTO.setGender(participant.getPet().getGender());
                    petDTO.setWeight(participant.getPet().getWeight());
                    petDTO.setPersonality(participant.getPet().getPersonality());
                    return petDTO;
                })
                .collect(Collectors.toList());
        dto.setPets(pets);

        // petType 필드 설정 (모든 펫 타입을 쉼표로 구분)
        String petTypes = pets.stream()
                .map(PetDTO::getType)
                .distinct()
                .collect(Collectors.joining(", "));
        dto.setPetType(petTypes);

        // 현재 참가 인원 수 계산 및 설정
        long currentParticipants = room.getParticipants().stream()
                .filter(p -> p.getStatus() == RoomParticipant.ParticipantStatus.Accepted)
                .count();
        dto.setCurrentParticipants(currentParticipants);

        // 호스트 정보 설정
        dto.setMhost(convertMemberToDto(room.getMhost()));

        return dto;
    }

    /**
     * Member 엔티티를 MemberDTO로 변환
     *
     * @param member Member 엔티티
     * @return MemberDTO 객체
     */
    private MemberDTO convertMemberToDto(Member member) {
        MemberDTO dto = new MemberDTO();
        dto.setMid(member.getMid());
        dto.setName(member.getName());
        dto.setEmail(member.getEmail());
        // 필요한 경우 추가 필드 설정
        return dto;
    }
}
