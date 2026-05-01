package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Function;
import software.constructs.Construct;

/**
 * Creates the five Appointment Lambda functions and grants DynamoDB permissions.
 * Route wiring is handled in TherapyApiStack via OpenApiSpecProcessor.
 *
 *   POST   /appointments                    → createAppointment
 *   GET    /appointments                    → listAppointments
 *   GET    /appointments/{appointmentId}    → getAppointment
 *   PATCH  /appointments/{appointmentId}    → patchAppointment
 *   DELETE /appointments/{appointmentId}    → deleteAppointment
 */
public class AppointmentsApiConstruct extends Construct {

    private final Function createAppointment;
    private final Function listAppointments;
    private final Function getAppointment;
    private final Function patchAppointment;
    private final Function deleteAppointment;

    public AppointmentsApiConstruct(Construct scope, String id,
                                    LambdaFactory factory,
                                    Table appointmentTable,
                                    Table sessionTable,
                                    Table mappingTable) {
        super(scope, id);

        createAppointment = factory.create("CreateAppointmentFunction",
                "com.therapy.handler.appointment.CreateAppointmentHandler");
        listAppointments  = factory.create("ListAppointmentsFunction",
                "com.therapy.handler.appointment.ListAppointmentsHandler");
        getAppointment    = factory.create("GetAppointmentFunction",
                "com.therapy.handler.appointment.GetAppointmentHandler");
        patchAppointment  = factory.create("PatchAppointmentFunction",
                "com.therapy.handler.appointment.PatchAppointmentHandler");
        deleteAppointment = factory.create("DeleteAppointmentFunction",
                "com.therapy.handler.appointment.DeleteAppointmentHandler");

        appointmentTable.grantReadWriteData(createAppointment);
        appointmentTable.grantReadWriteData(listAppointments);
        appointmentTable.grantReadData(getAppointment);
        appointmentTable.grantReadWriteData(patchAppointment);
        appointmentTable.grantReadWriteData(deleteAppointment);

        // createAppointment needs to read sessions and mappings for precondition checks
        sessionTable.grantReadWriteData(createAppointment);
        mappingTable.grantReadData(createAppointment);

        // listAppointments needs to read sessions (therapist ownership check)
        sessionTable.grantReadData(listAppointments);

        // patchAppointment needs to update session (mark booked, pendingCount)
        sessionTable.grantReadWriteData(patchAppointment);
    }

    public Function getCreateAppointment() { return createAppointment; }
    public Function getListAppointments()  { return listAppointments; }
    public Function getGetAppointment()    { return getAppointment; }
    public Function getPatchAppointment()  { return patchAppointment; }
    public Function getDeleteAppointment() { return deleteAppointment; }
}
