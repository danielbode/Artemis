import { Color, ScaleType } from '@swimlane/ngx-charts';
import * as shape from 'd3-shape';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Subscription } from 'rxjs';
import { ExamMonitoringWebsocketService } from '../exam-monitoring-websocket.service';
import { ActivatedRoute } from '@angular/router';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';

export abstract class ChartComponent {
    // Subscriptions
    protected routeSubscription?: Subscription;
    protected examActionSubscription?: Subscription;

    // Exam
    protected examId: number;
    protected courseId: number;

    // Actions
    filteredExamActions: ExamAction[] = [];

    chartIdentifierKey = '';

    showNumberLastTimeStamps = 10;
    timeStampGapInSeconds = 15;

    // Chart
    ngxData: NgxChartsEntry[] = [];
    ngxColor: Color;
    curve: any = shape.curveMonotoneX;
    legend: boolean;
    routerLink: any[];

    protected constructor(
        private route: ActivatedRoute,
        private examMonitoringWebsocketService: ExamMonitoringWebsocketService,
        chartIdentifierKey: string,
        legend: boolean,
        colors?: string[],
    ) {
        this.chartIdentifierKey = chartIdentifierKey;
        this.legend = legend;

        this.ngxColor = {
            name: this.chartIdentifierKey,
            selectable: true,
            group: ScaleType.Ordinal,
            domain: colors ?? [],
        };
    }

    /**
     * Inits all subscriptions.
     * @protected
     */
    protected initSubscriptions() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examActionSubscription = this.examMonitoringWebsocketService.getExamMonitoringObservable(this.examId)?.subscribe((examAction) => {
            if (examAction) {
                this.filteredExamActions.push(examAction);
            }
        });
    }

    /**
     * Unsubscribes from all subscriptions.
     * @protected
     */
    protected endSubscriptions() {
        this.examActionSubscription?.unsubscribe();
    }

    protected initRenderRate(seconds: number) {
        // Trigger change detection every x seconds and filter the data
        setInterval(() => {
            this.filteredExamActions = [...this.filteredExamActions.filter((action) => this.filterRenderedData(action))];
            this.updateData();
        }, seconds * 1000);
    }

    /**
     * Create and initialize the data for the chart.
     */
    abstract initData(): void;

    /**
     * Updates the data for the chart.
     */
    abstract updateData(): void;

    /**
     * The default case is that we don't filter any actions. This filter is adapted in subclasses.
     * @param examAction
     */
    filterRenderedData(examAction: ExamAction): boolean {
        return true;
    }

    /**
     * Method to filter actions which are not in the specified time frame.
     * @param examAction received action
     * @protected
     */
    protected filterActionsNotInTimeframe(examAction: ExamAction) {
        return ceilDayjsSeconds(dayjs(), this.timeStampGapInSeconds)
            .subtract(this.showNumberLastTimeStamps * this.timeStampGapInSeconds, 'seconds')
            .isBefore(examAction.ceiledTimestamp ?? examAction.timestamp);
    }

    /**
     * Method to get the last x time stamps.
     */
    public getLastXTimestamps() {
        const ceiledNow = ceilDayjsSeconds(dayjs(), 15);
        const timestamps = [];
        for (let i = this.showNumberLastTimeStamps - 1; i >= 0; i--) {
            timestamps.push(ceiledNow.subtract(i * this.timeStampGapInSeconds, 'seconds'));
        }
        return timestamps;
    }
}
