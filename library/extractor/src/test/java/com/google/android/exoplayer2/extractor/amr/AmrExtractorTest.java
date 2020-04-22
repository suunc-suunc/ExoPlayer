/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor.amr;

import static com.google.android.exoplayer2.extractor.amr.AmrExtractor.amrSignatureNb;
import static com.google.android.exoplayer2.extractor.amr.AmrExtractor.amrSignatureWb;
import static com.google.android.exoplayer2.extractor.amr.AmrExtractor.frameSizeBytesByTypeNb;
import static com.google.android.exoplayer2.extractor.amr.AmrExtractor.frameSizeBytesByTypeWb;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit test for {@link AmrExtractor}. */
// TODO(ibaker): Split this into two test classes: one parameterized, and one not.
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class AmrExtractorTest {

  private static final Random RANDOM = new Random(1234);

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void sniff_nonAmrSignature_returnFalse() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();
    FakeExtractorInput input = fakeExtractorInputWithData(Util.getUtf8Bytes("0#!AMR\n123"));

    boolean result = amrExtractor.sniff(input);
    assertThat(result).isFalse();
  }

  @Test
  public void read_nonAmrSignature_throwParserException() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();
    FakeExtractorInput input = fakeExtractorInputWithData(Util.getUtf8Bytes("0#!AMR-WB\n"));

    try {
      amrExtractor.read(input, new PositionHolder());
      fail();
    } catch (ParserException e) {
      // expected
    }
  }

  @Test
  public void read_amrNb_returnParserException_forInvalidFrameType() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();

    // Frame type 12-14 for narrow band is reserved for future usage.
    byte[] amrFrame = newNarrowBandAmrFrameWithType(12);
    byte[] data = joinData(amrSignatureNb(), amrFrame);
    FakeExtractorInput input = fakeExtractorInputWithData(data);

    try {
      amrExtractor.read(input, new PositionHolder());
      fail();
    } catch (ParserException e) {
      // expected
    }
  }

  @Test
  public void read_amrWb_returnParserException_forInvalidFrameType() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();

    // Frame type 10-13 for wide band is reserved for future usage.
    byte[] amrFrame = newWideBandAmrFrameWithType(13);
    byte[] data = joinData(amrSignatureWb(), amrFrame);
    FakeExtractorInput input = fakeExtractorInputWithData(data);

    try {
      amrExtractor.read(input, new PositionHolder());
      fail();
    } catch (ParserException e) {
      // expected
    }
  }

  @Test
  public void read_amrNb_returnEndOfInput_ifInputEncountersEoF() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();

    byte[] amrFrame = newNarrowBandAmrFrameWithType(3);
    byte[] data = joinData(amrSignatureNb(), amrFrame);
    FakeExtractorInput input = fakeExtractorInputWithData(data);

    // Read 1st frame, which will put the input at EoF.
    amrExtractor.read(input, new PositionHolder());

    int result = amrExtractor.read(input, new PositionHolder());
    assertThat(result).isEqualTo(Extractor.RESULT_END_OF_INPUT);
  }

  @Test
  public void read_amrWb_returnEndOfInput_ifInputEncountersEoF() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();

    byte[] amrFrame = newWideBandAmrFrameWithType(5);
    byte[] data = joinData(amrSignatureWb(), amrFrame);
    FakeExtractorInput input = fakeExtractorInputWithData(data);

    // Read 1st frame, which will put the input at EoF.
    amrExtractor.read(input, new PositionHolder());

    int result = amrExtractor.read(input, new PositionHolder());
    assertThat(result).isEqualTo(Extractor.RESULT_END_OF_INPUT);
  }

  @Test
  public void read_amrNb_returnParserException_forInvalidFrameHeader() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();

    byte[] invalidHeaderFrame = newNarrowBandAmrFrameWithType(4);

    // The padding bits are at bit-1 positions in the following pattern: 1000 0011
    // Padding bits must be 0.
    invalidHeaderFrame[0] = (byte) (invalidHeaderFrame[0] | 0b01111101);

    byte[] data = joinData(amrSignatureNb(), invalidHeaderFrame);
    FakeExtractorInput input = fakeExtractorInputWithData(data);

    try {
      amrExtractor.read(input, new PositionHolder());
      fail();
    } catch (ParserException e) {
      // expected
    }
  }

  @Test
  public void read_amrWb_returnParserException_forInvalidFrameHeader() throws IOException {
    AmrExtractor amrExtractor = setupAmrExtractorWithOutput();

    byte[] invalidHeaderFrame = newWideBandAmrFrameWithType(6);

    // The padding bits are at bit-1 positions in the following pattern: 1000 0011
    // Padding bits must be 0.
    invalidHeaderFrame[0] = (byte) (invalidHeaderFrame[0] | 0b01111110);

    byte[] data = joinData(amrSignatureWb(), invalidHeaderFrame);
    FakeExtractorInput input = fakeExtractorInputWithData(data);

    try {
      amrExtractor.read(input, new PositionHolder());
      fail();
    } catch (ParserException e) {
      // expected
    }
  }

  @Test
  public void extractingNarrowBandSamples() throws Exception {
    ExtractorAsserts.assertBehavior(
        createAmrExtractorFactory(/* withSeeking= */ false), "amr/sample_nb.amr", assertionConfig);
  }

  @Test
  public void extractingWideBandSamples() throws Exception {
    ExtractorAsserts.assertBehavior(
        createAmrExtractorFactory(/* withSeeking= */ false), "amr/sample_wb.amr", assertionConfig);
  }

  @Test
  public void extractingNarrowBandSamples_withSeeking() throws Exception {
    ExtractorAsserts.assertBehavior(
        createAmrExtractorFactory(/* withSeeking= */ true),
        "amr/sample_nb_cbr.amr",
        assertionConfig);
  }

  @Test
  public void extractingWideBandSamples_withSeeking() throws Exception {
    ExtractorAsserts.assertBehavior(
        createAmrExtractorFactory(/* withSeeking= */ true),
        "amr/sample_wb_cbr.amr",
        assertionConfig);
  }

  private byte[] newWideBandAmrFrameWithType(int frameType) {
    byte frameHeader = (byte) ((frameType << 3) & (0b01111100));
    int frameContentInBytes = frameSizeBytesByTypeWb(frameType) - 1;

    return joinData(new byte[] {frameHeader}, randomBytesArrayWithLength(frameContentInBytes));
  }

  private byte[] newNarrowBandAmrFrameWithType(int frameType) {
    byte frameHeader = (byte) ((frameType << 3) & (0b01111100));
    int frameContentInBytes = frameSizeBytesByTypeNb(frameType) - 1;

    return joinData(new byte[] {frameHeader}, randomBytesArrayWithLength(frameContentInBytes));
  }

  private static byte[] randomBytesArrayWithLength(int length) {
    byte[] result = new byte[length];
    RANDOM.nextBytes(result);
    return result;
  }

  private static byte[] joinData(byte[]... byteArrays) {
    int totalLength = 0;
    for (byte[] byteArray : byteArrays) {
      totalLength += byteArray.length;
    }
    byte[] result = new byte[totalLength];
    int offset = 0;
    for (byte[] byteArray : byteArrays) {
      System.arraycopy(byteArray, /* srcPos= */ 0, result, offset, byteArray.length);
      offset += byteArray.length;
    }
    return result;
  }

  private static AmrExtractor setupAmrExtractorWithOutput() {
    AmrExtractor amrExtractor = new AmrExtractor();
    FakeExtractorOutput output = new FakeExtractorOutput();
    amrExtractor.init(output);
    return amrExtractor;
  }

  private static FakeExtractorInput fakeExtractorInputWithData(byte[] data) {
    return new FakeExtractorInput.Builder().setData(data).build();
  }

  private static ExtractorAsserts.ExtractorFactory createAmrExtractorFactory(boolean withSeeking) {
    return () -> {
      if (!withSeeking) {
        return new AmrExtractor();
      } else {
        return new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);
      }
    };
  }
}
