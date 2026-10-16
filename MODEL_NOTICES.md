# Model sources and attribution

This notice describes the public speech and environmental-noise sources recorded for the bundled
`jmbe-ml-v4-20261014` model in the optional 16 kHz branch. The model combines the frozen 8 kHz speech-enhancement
weights with the selected V4 bandwidth-extension weights for IMBE and AMBE+2. The resource contains numerical
weights, fixed inference constants and a manifest. No training recordings, captured radio calls, reference speech,
transcripts or training examples are included in this repository or its model bundle.

The model bundle SHA-256 is `3779c29761d97233873a991c5912e341dc2614c82f7b80dcd3b354eda994ed18`.

## Speech: LibriSpeech

Speech came from the **LibriSpeech ASR corpus, `dev-clean` subset**, prepared by **Vassil Panayotov**, with the
assistance of **Daniel Povey**, from LibriVox audiobook recordings. The retained corpus notice credits
“LibriSpeech (c) 2014 by Vassil Panayotov.”

- Dataset: [OpenSLR SLR12: LibriSpeech ASR corpus](https://www.openslr.org/12/).
- Selected archive: [dev-clean.tar.gz](https://www.openslr.org/resources/12/dev-clean.tar.gz).
- Source-data license: [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/).
- Citation: Vassil Panayotov, Guoguo Chen, Daniel Povey and Sanjeev Khudanpur, *LibriSpeech: an ASR corpus based on
  public domain audio books*, ICASSP 2015. [Paper](https://www.danielpovey.com/files/2015_icassp_librispeech.pdf).

The pilot used its own speaker-disjoint partitions within `dev-clean`; those partitions are not official
LibriSpeech evaluation splits. Preparation included level calibration, resampling, codec encoding/decoding and
clean/noisy pairs at 20 dB and 10 dB signal-to-noise ratios. LibriVox and Project Gutenberg volunteers are also
acknowledged by the corpus creators.

## Environmental noise: MS-SNSD

Environmental-noise augmentation came from the **Microsoft Scalable Noisy Speech Dataset (MS-SNSD)**, at source
revision `fe61c4ba0d9ac8dd7e23d719cc79f8947e1dc742`.

Dataset authors: **Chandan K. A. Reddy, Ebrahim Beyrami, Jamie Pool, Ross Cutler, Sriram Srinivasan and
Johannes Gehrke**. Citation: *A Scalable Noisy Speech Dataset and Online Subjective Test Framework*,
Interspeech 2019, pp. 1816–1820.

- Dataset and original notices: [Microsoft MS-SNSD](https://github.com/microsoft/MS-SNSD/tree/fe61c4ba0d9ac8dd7e23d719cc79f8947e1dc742).
- [Publication](https://www.isca-speech.org/archive/Interspeech_2019/pdfs/3087.pdf).

The retained source list identifies these exact files. The `_1` recordings were used for training augmentation;
`_2` recordings were retained for validation.

| Use | Recording and pinned source |
| --- | --- |
| Training | [AirConditioner_1.wav](https://raw.githubusercontent.com/microsoft/MS-SNSD/fe61c4ba0d9ac8dd7e23d719cc79f8947e1dc742/noise_train/AirConditioner_1.wav) |
| Training | [VacuumCleaner_1.wav](https://raw.githubusercontent.com/microsoft/MS-SNSD/fe61c4ba0d9ac8dd7e23d719cc79f8947e1dc742/noise_train/VacuumCleaner_1.wav) |
| Validation | [AirConditioner_2.wav](https://raw.githubusercontent.com/microsoft/MS-SNSD/fe61c4ba0d9ac8dd7e23d719cc79f8947e1dc742/noise_train/AirConditioner_2.wav) |
| Validation | [VacuumCleaner_2.wav](https://raw.githubusercontent.com/microsoft/MS-SNSD/fe61c4ba0d9ac8dd7e23d719cc79f8947e1dc742/noise_train/VacuumCleaner_2.wav) |

The MS-SNSD source README identifies two environmental-noise source families:

- **Freesound contributors**, with selected recordings described upstream as
  [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). [Freesound](https://freesound.org/).
- **DEMAND: Diverse Environments Multichannel Acoustic Noise Database**, by **Joachim Thiemann, Nobutaka Ito and
  Emmanuel Vincent**, under [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
  [Original dataset and attribution](https://zenodo.org/records/1227121).

The retained MS-SNSD notices do not identify an individual original creator or license for each selected WAV.
This notice therefore records both upstream source families without assigning an unverified per-file license.
MS-SNSD's Microsoft MIT **code** license is separate from these **audio-data** notices. No MS-SNSD source code or
noise recording is included in the model bundle.

## JMBE code and scope

JMBE source code retains its existing GNU General Public License notices; see [LICENSE](LICENSE) and the individual
source headers. Dataset licenses above identify the recorded source-data terms. This notice makes no separate
licensing assertion about trained numeric weights and does not imply endorsement by the dataset creators.
