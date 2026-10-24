package com.systemdesign.booking;

import java.util.List;

public record CreateGroupBookingRequest(
        List<Long> showSeatIds
) {}