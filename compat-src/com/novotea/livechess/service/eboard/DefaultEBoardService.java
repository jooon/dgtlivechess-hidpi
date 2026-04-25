package com.novotea.livechess.service.eboard;

import com.novotea.chess.eboard.EBoardState;
import com.novotea.chess.util.Listeners;
import com.novotea.chess.util.service.RegisterService;
import com.novotea.chess.util.service.ServiceAccess;
import com.novotea.chess.util.task.TaskScheduler;
import com.novotea.chess.util.task.TaskService;
import com.novotea.dgt.frame.FrameException;
import com.novotea.dgt.frame.EBoardFrame;
import com.novotea.dgt.frame.FrameType;
import com.novotea.dgt.pcr.EBoardFramePCRRecord;
import com.novotea.dgt.pcr.FilePCR;
import com.novotea.dgt.pcr.PCRException;
import com.novotea.dgt.pcr.PCRRecord;
import com.novotea.livechess.api.LiveChessService;
import com.novotea.livechess.api.error.LiveChessError;
import com.novotea.livechess.api.model.EBoardInfo;
import com.novotea.livechess.api.services.EBoardResource;
import com.novotea.livechess.api.services.EBoardService;
import com.novotea.livechess.api.services.PersistencyService;
import com.novotea.livechess.api.services.ShutdownService;
import com.novotea.livechess.service.ResourceMap;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RegisterService(EBoardService.class)
public class DefaultEBoardService implements EBoardService, LiveChessService {
  private static final String VIRTUAL_SERIAL_PREFIX = "virt";
  private static final int VIRTUAL_BOARD_COUNT = 8;
  private static final String VIRTUAL_TYPE = "Virtual";
  private static final String VIRTUAL_VERSION = "1.0";
  private static final String VIRTUAL_COMMENT = "Software-only e-board";
  private static final byte BEGIN_OPCODE = (byte) 111;
  private static final List<String> VIRTUAL_SERIALS = buildVirtualSerials();

  private final PersistencyService service;
  private final ResourceMap<String, EBoardInfo, DefaultEBoardResource> map;
  private final File directory;
  private final EBoardService.ServiceListener listeners;
  private final TaskScheduler executor;
  private final List<EBoardService.DetectionListener> detectionListeners;

  public DefaultEBoardService() throws IOException {
    service = (PersistencyService) ServiceAccess.get(PersistencyService.class);
    listeners =
        (EBoardService.ServiceListener)
            Listeners.newInstance(this, EBoardService.ServiceListener.class);
    executor =
        ((TaskService) ServiceAccess.get(TaskService.class)).create("E-board Service", true);
    map = new ResourceMap<>(executor, service.service(EBoardInfo.class), this::key);
    directory = service.directory(map.info());
    detectionListeners = new CopyOnWriteArrayList<>();

    ensureVirtualBoardInfo();

    ServiceAccess.register(this);
    ((ShutdownService) ServiceAccess.get(ShutdownService.class)).register(map::dispose);
  }

  @Override
  public void start() throws Exception {}

  @Override
  public void addDetectionListener(EBoardService.DetectionListener listener) {
    detectionListeners.add(listener);
  }

  @Override
  public void removeDetectionListener(EBoardService.DetectionListener listener) {
    detectionListeners.remove(listener);
  }

  private String key(EBoardInfo value) {
    return value.getSerialNr();
  }

  @Override
  public boolean remove(String serialNr) {
    if (isVirtualBoard(serialNr)) {
      return false;
    }

    EBoardInfo info = map.get(serialNr);
    if (info == null) {
      return false;
    }

    boolean notBusy = info.getLiveGame() == null || info.getLiveGame().getPairing() == null;
    if (info.getState() != null || !notBusy) {
      return false;
    }

    map.remove(info);
    return true;
  }

  @Override
  public String toString() {
    return "e-Board service";
  }

  @Override
  public synchronized DefaultEBoardResource resource(String serialNr) {
    if (isVirtualBoard(serialNr)) {
      ensureVirtualBoardInfo();
    }

    DefaultEBoardResource resource = map.resource(serialNr);
    if (resource != null) {
      return resource;
    }

    EBoardInfo info = map.get(serialNr);
    if (info == null) {
      info = EBoardInfo.newInstance(serialNr);
      if (isVirtualBoard(serialNr)) {
        configureVirtualBoard(info);
      }
    }
    return create(info);
  }

  @Override
  public EBoardInfo get(String serialNr) {
    if (isVirtualBoard(serialNr)) {
      ensureVirtualBoardInfo();
    }
    return resource(serialNr).getInfo();
  }

  @Override
  public DefaultEBoardResource find(String serialNr) {
    if (isVirtualBoard(serialNr)) {
      ensureVirtualBoardInfo();
    }

    DefaultEBoardResource resource = map.resource(serialNr);
    if (resource != null) {
      return resource;
    }

    EBoardInfo info = map.get(serialNr);
    if (info == null) {
      return null;
    }
    return create(info);
  }

  private DefaultEBoardResource create(EBoardInfo info) {
    try {
      boolean resetVirtualBoard =
          isVirtualBoard(info.getSerialNr()) && !VIRTUAL_VERSION.equals(info.getVersion());
      if (isVirtualBoard(info.getSerialNr())) {
        configureVirtualBoard(info);
        ensureVirtualBoardPCR(new File(directory, info.getSerialNr()), resetVirtualBoard);
      }
      DefaultEBoardResource resource =
          new DefaultEBoardResource(this, info, new File(directory, info.getSerialNr()));
      map.add(info, resource);
      listeners.eboardActive(resource);
      return resource;
    } catch (IOException e) {
      LiveChessError.unableToReadEBoardPCRFile(info.getSerialNr(), e);
      return null;
    }
  }

  @Override
  public List<EBoardInfo> list() {
    ensureVirtualBoardInfo();
    return map.list();
  }

  public void snoop(EBoardResource resource, EBoardFrame<? extends FrameType> frame) {
    for (EBoardService.DetectionListener listener : detectionListeners) {
      listener.detected(resource);
    }
  }

  @Override
  public void addListener(EBoardService.ServiceListener listener) {
    Listeners.addListener(listeners, listener);
  }

  @Override
  public void removeListener(EBoardService.ServiceListener listener) {
    Listeners.removeListener(listeners, listener);
  }

  private void ensureVirtualBoardInfo() {
    for (String serialNr : VIRTUAL_SERIALS) {
      EBoardInfo info = map.get(serialNr);
      if (info == null) {
        info = EBoardInfo.newInstance(serialNr);
        configureVirtualBoard(info);
        map.add(info);
        continue;
      }

      configureVirtualBoard(info);
    }
  }

  private void configureVirtualBoard(EBoardInfo info) {
    info.setType(VIRTUAL_TYPE);
    info.setVersion(VIRTUAL_VERSION);
    if (info.getComment() == null || info.getComment().trim().isEmpty()) {
      info.setComment(VIRTUAL_COMMENT);
    }
    info.setState(EBoardState.ACTIVE);
  }

  private boolean isVirtualBoard(String serialNr) {
    return VIRTUAL_SERIALS.contains(serialNr);
  }

  private static List<String> buildVirtualSerials() {
    List<String> serials = new ArrayList<>(VIRTUAL_BOARD_COUNT);
    for (int index = 1; index <= VIRTUAL_BOARD_COUNT; index++) {
      String suffix = index < 10 ? "0" + index : Integer.toString(index);
      serials.add(VIRTUAL_SERIAL_PREFIX + suffix);
    }
    return Collections.unmodifiableList(serials);
  }

  private void ensureVirtualBoardPCR(File boardDirectory, boolean reset) throws IOException {
    boardDirectory.mkdirs();
    File pcrFile = new File(boardDirectory, "eboard.pcr");
    FilePCR pcr = null;
    try {
      if (reset && pcrFile.exists() && !pcrFile.delete()) {
        throw new IOException("Unable to reset virtual board PCR: " + pcrFile);
      }

      pcr = new FilePCR(pcrFile, true);
      if (!reset && !hasBeginOnlyPosition(pcr)) {
        pcr.close();
        pcr = null;
        if (pcrFile.exists() && !pcrFile.delete()) {
          throw new IOException("Unable to recreate virtual board PCR: " + pcrFile);
        }
        pcr = new FilePCR(pcrFile, true);
      }

      if (pcr.get(pcr.start()) != null) {
        return;
      }
      seedVirtualBoard(pcr);
    } catch (PCRException | FrameException e) {
      throw new IOException("Unable to seed virtual board PCR", e);
    } finally {
      if (pcr != null) {
        pcr.close();
      }
    }
  }

  private void seedVirtualBoard(FilePCR pcr) throws IOException, FrameException {
    addRecord(pcr, FrameType.EESTART, System.currentTimeMillis(), new byte[] {BEGIN_OPCODE});
  }

  private void addRecord(FilePCR pcr, FrameType type, long timestamp, byte[] payload)
      throws IOException, FrameException {
    pcr.add(EBoardFramePCRRecord.newInstance(type, timestamp, ByteBuffer.wrap(payload)));
  }

  private boolean hasBeginOnlyPosition(FilePCR pcr) throws IOException, PCRException {
    PCRRecord first = pcr.get(pcr.start());
    if (first == null) {
      return false;
    }

    if (!"EEStart[BEGIN]".equals(first.toString())) {
      return false;
    }

    return pcr.get(pcr.start() + first.pcrSize()) == null;
  }
}
