## AutomatedBlotQuantification

I wrote C# code for the Unity game engine that automatically quantifies western blots, segmenting the area of each blot of interest and comparing it to a reference blot.

## Quick start

This repository implements automated quantification of western blots
by area segmentation, in two independent implementations. See
[DEPENDENCIES.md](DEPENDENCIES.md) for the ImageJ and Unity/OpenCV for
Unity requirements.

## Repository contents

- `JBlots.java`, `j_blots.jar` -- the ImageJ plugin (source and
  compiled build).
- `WesternBlotSegmentationAnalysis.cs` -- the Unity C# function.
- [`media/`](media/) -- an example screenshot.
- **License:** see [LICENSE](LICENSE) -- research/educational use.

## About

My work on JBlotQuant, the ImageJ Java plug-in Nik Mihaylov wrote under my guidance based on the algorithmic steps I designed (see the PNG screenshot).

For detailed information, see: https://www.researchgate.net/publication/382593670_Quantitative_Video_Microscopy_in_Medicine
