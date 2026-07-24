package kh.edu.istad.ite.features.user.repository;


import kh.edu.istad.ite.features.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository
        extends JpaRepository<UserProfile, String> {
}