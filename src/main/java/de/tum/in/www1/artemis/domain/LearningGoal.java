package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@Entity
@Table(name = "learning_goal")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LearningGoal extends DomainObject {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    @Lob
    private String description;

    /**
     * The type of learning goal according to Bloom's taxonomy.
     * @see <a href="https://en.wikipedia.org/wiki/Bloom%27s_taxonomy">Wikipedia</a>
     */
    @Column(name = "type")
    private String type;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties("learningGoals")
    private Course course;

    @ManyToMany
    @JoinTable(name = "learning_goal_exercise", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "learningGoals", "lecture" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Exercise> exercises = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "learning_goal_lecture_unit", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "lecture_unit_id", referencedColumnName = "id"))
    @JsonIgnoreProperties("learningGoals")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<LectureUnit> lectureUnits = new HashSet<>();

    /**
     * A set of courses for which this learning goal is a prerequisite for.
     */
    @ManyToMany
    @JoinTable(name = "learning_goal_course", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "course_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "learningGoals", "prerequisites" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Course> consecutiveCourses = new HashSet<>();

    /**
     * The parent (higher-level) learning goal on the same topic that this learning goal extends.
     * Reverse relation of {@link #subLearningGoals}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({ "course", "subLearningGoals" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private LearningGoal parentLearningGoal;

    /**
     * A set of (more detailed) learning goals on same topic that extend this learning goal.
     * Reverse relation of {@link #parentLearningGoal}.
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "parentLearningGoal")
    @JsonIgnoreProperties({ "course", "parentLearningGoal" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<LearningGoal> subLearningGoals = new HashSet<>();

    /**
     * A set of learning goals that are required/presumed to fulfill this learning goal.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "learning_goal_hierarchy", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "assumed_learning_goal_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "course" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<LearningGoal> presumedLearningGoals = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public void addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.getLearningGoals().add(this);
    }

    public void removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.getLearningGoals().remove(this);
    }

    public Set<LectureUnit> getLectureUnits() {
        return lectureUnits;
    }

    public void setLectureUnits(Set<LectureUnit> lectureUnits) {
        this.lectureUnits = lectureUnits;
    }

    public void addLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnits.add(lectureUnit);
        lectureUnit.getLearningGoals().add(this);
    }

    public void removeLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnits.remove(lectureUnit);
        lectureUnit.getLearningGoals().remove(this);
    }

    public Set<Course> getConsecutiveCourses() {
        return consecutiveCourses;
    }

    public void setConsecutiveCourses(Set<Course> consecutiveCourses) {
        this.consecutiveCourses = consecutiveCourses;
    }

    public LearningGoal getParentLearningGoal() {
        return parentLearningGoal;
    }

    public void setParentLearningGoal(LearningGoal parentLearningGoal) {
        this.parentLearningGoal = parentLearningGoal;
    }

    public Set<LearningGoal> getSubLearningGoals() {
        return subLearningGoals;
    }

    public void setSubLearningGoals(Set<LearningGoal> subLearningGoals) {
        this.subLearningGoals = subLearningGoals;
    }

    public Set<LearningGoal> getPresumedLearningGoals() {
        return presumedLearningGoals;
    }

    public void setPresumedLearningGoals(Set<LearningGoal> presumedLearningGoals) {
        this.presumedLearningGoals = presumedLearningGoals;
    }

    public enum LearningGoalSearchColumn {

        ID("id"), TITLE("title"), COURSE_TITLE("course.title"), SEMESTER("course.semester");

        private final String mappedColumnName;

        LearningGoalSearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }
}
