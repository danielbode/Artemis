import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { type UMLDiagramType, UMLDiagramType as UMLDiagramTypes } from '@ls1intum/apollon';

export class ModelingExercise extends Exercise {
    public diagramType?: UMLDiagramType;
    public exampleSolutionModel?: string;
    public exampleSolutionExplanation?: string;

    constructor(diagramType: UMLDiagramType, course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.MODELING);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.diagramType = diagramType;
        // default value

        this.assessmentType = AssessmentType.MANUAL;
        if (this.diagramType === UMLDiagramTypes.ClassDiagram || this.diagramType === UMLDiagramTypes.ActivityDiagram) {
            this.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        }
    }
}
