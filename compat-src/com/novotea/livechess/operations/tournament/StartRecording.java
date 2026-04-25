package com.novotea.livechess.operations.tournament;

import com.novotea.chess.util.service.ServiceAccess;
import com.novotea.dgt.frame.FrameType;
import com.novotea.dgt.pcr.EBoardFramePCRRecord;
import com.novotea.dgt.pcr.FilePCR;
import com.novotea.dgt.pcr.PCRRecord;
import com.novotea.livechess.api.APIOperation;
import com.novotea.livechess.api.model.EBoardInfo;
import com.novotea.livechess.api.model.Pairing;
import com.novotea.livechess.api.services.EBoardResource;
import com.novotea.livechess.api.services.EBoardService;
import com.novotea.livechess.api.services.TournamentService;
import com.novotea.livechess.service.recording.DefaultLiveGame;
import com.novotea.livechess.service.recording.EBoardRecorder;
import com.novotea.livechess.service.tournament.DefaultTournamentService;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@APIOperation.Description("Record new games")
public class StartRecording extends APIOperation {
  private static final String VIRTUAL_SERIAL_PREFIX = "virt";
  private static final int VIRTUAL_BOARD_COUNT = 8;
  private static final byte BEGIN_OPCODE = (byte) 111;

  private final List<Pairing> pairings;

  public StartRecording(Collection<Pairing> pairings) {
    this.pairings = new ArrayList<>(pairings);
  }

  @Override
  public Report check() {
    return TournamentSupport.busyCheck(ok(), pairings);
  }

  @Override
  protected Report run() throws Throwable {
    TournamentService service = (TournamentService) ServiceAccess.get(TournamentService.class);
    EBoardService eBoardService = (EBoardService) ServiceAccess.get(EBoardService.class);
    for (Pairing pairing : pairings) {
      injectBeginPositionIfVirtual(service, eBoardService, pairing);
      service.startRecording(pairing);
    }
    return ok();
  }

  private void injectBeginPositionIfVirtual(
      TournamentService tournamentService, EBoardService eBoardService, Pairing pairing)
      throws Throwable {
    if (!(tournamentService instanceof DefaultTournamentService)) {
      return;
    }

    EBoardInfo info = ((DefaultTournamentService) tournamentService).getEBoardInfo(pairing);
    if (info == null || !isVirtualBoard(info.getSerialNr())) {
      return;
    }

    EBoardResource resource = eBoardService.resource(info.getSerialNr());
    if (resource == null) {
      return;
    }

    FilePCR pcr = getPCR(resource);
    resetVirtualLiveGame(info, resource, pcr.size());

    PCRRecord record =
        EBoardFramePCRRecord.newInstance(
            FrameType.EESTART,
            System.currentTimeMillis(),
            ByteBuffer.wrap(new byte[] {BEGIN_OPCODE}));
    if (record == null) {
      return;
    }

    Field listenersField = resource.getClass().getDeclaredField("listeners");
    listenersField.setAccessible(true);
    EBoardResource.Listener listeners = (EBoardResource.Listener) listenersField.get(resource);

    int offset = pcr.add(record);
    if (listeners != null) {
      listeners.update(resource, offset, record);
    }
  }

  private FilePCR getPCR(EBoardResource resource) throws ReflectiveOperationException {
    Field pcrField = resource.getClass().getDeclaredField("pcr");
    pcrField.setAccessible(true);
    return (FilePCR) pcrField.get(resource);
  }

  private void resetVirtualLiveGame(EBoardInfo info, EBoardResource resource, int startOffset)
      throws ReflectiveOperationException {
    Object game = info.getLiveGame();
    if (!(game instanceof DefaultLiveGame)) {
      return;
    }

    EBoardRecorder recorder = ((DefaultLiveGame) game).getRecorder();
    if (recorder == null) {
      return;
    }

    DefaultLiveGame resetGame = new DefaultLiveGame(recorder, startOffset, resource);
    if (recorder.getState() != null) {
      resetGame.state(recorder.getState());
    }

    Field gameField = EBoardRecorder.class.getDeclaredField("game");
    gameField.setAccessible(true);
    gameField.set(recorder, resetGame);

    info.setLiveGame(resetGame);
  }

  private boolean isVirtualBoard(String serialNr) {
    if (serialNr == null || !serialNr.startsWith(VIRTUAL_SERIAL_PREFIX)) {
      return false;
    }

    String suffix = serialNr.substring(VIRTUAL_SERIAL_PREFIX.length());
    if (suffix.length() != 2) {
      return false;
    }

    try {
      int index = Integer.parseInt(suffix);
      return index >= 1 && index <= VIRTUAL_BOARD_COUNT;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
