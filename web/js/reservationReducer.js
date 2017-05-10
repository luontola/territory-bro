// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {RESERVATION_MADE, RESERVATION_OFFER_RECEIVED} from "./reservationActions";

const reservation = (state = {}, action) => {
  switch (action.type) {
    case RESERVATION_OFFER_RECEIVED:
      return {
        ...state,
        id: state.id || action.offer.reservationId,
        offer: action.offer,
      };
    case RESERVATION_MADE:
      return {
        ...state,
        // TODO
        //id: action.reservation.reservationId,
        current: action.reservation,
      };
    default:
      return state;
  }
};

export default reservation;
